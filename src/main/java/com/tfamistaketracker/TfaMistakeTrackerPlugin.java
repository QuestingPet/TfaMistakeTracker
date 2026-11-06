package com.tfamistaketracker;

import com.google.inject.Provides;
import com.tfamistaketracker.detector.MistakeDetectorManager;
import com.tfamistaketracker.detector.tracker.VengeanceTracker;
import com.tfamistaketracker.events.InRaidChanged;
import com.tfamistaketracker.events.RaidEntered;
import com.tfamistaketracker.mistakestate.MistakeStateWriter;
import com.tfamistaketracker.panel.TfaMistakeTrackerPanel;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.util.List;

@Slf4j
@PluginDescriptor(
        name = "Tfa Mistake Tracker",
        internalName = "tfa-mistake-tracker"
)
public class TfaMistakeTrackerPlugin extends Plugin {

    public static final int CYCLES_PER_GAME_TICK = Constants.GAME_TICK_LENGTH / Constants.CLIENT_TICK_LENGTH;

    public static final String CONFIG_GROUP = "tfaMistakeTracker";
    static final String SHOW_SIDE_PANEL_CONFIG_KEY = "showSidePanel";

    private static final int OVERHEAD_TEXT_TICK_TIMEOUT = 5;
    private static final int CYCLES_FOR_OVERHEAD_TEXT = OVERHEAD_TEXT_TICK_TIMEOUT * CYCLES_PER_GAME_TICK;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private TfaMistakeTrackerConfig config;

    @Inject
    private MistakeDetectorManager mistakeDetectorManager;

    @Inject
    private RaidState raidState;

    @Inject
    private VengeanceTracker vengeanceTracker;

    @Inject
    private MistakeStateWriter mistakeStateWriter;

    // UI fields
    @Inject
    private ClientToolbar clientToolbar;
    private final BufferedImage icon = ImageUtil.loadImageResource(TfaMistakeTrackerPlugin.class, "panel-icon.png");
    private TfaMistakeTrackerPanel panel;
    private NavigationButton navButton;

    @Override
    protected void startUp() throws Exception {
        // Must be set before the panel is created, since the panel reads the mistake state on creation
        mistakeStateWriter.setPluginDirectory(getPluginDirectory());

        // Can't @Inject because we null it out in shutdown()
        panel = injector.getInstance(TfaMistakeTrackerPanel.class);

        // Add UI
        panel.loadHeaderIcon(icon);
        navButton = NavigationButton.builder()
                .tooltip("Tfa Mistake Tracker")
                .icon(icon)
                .priority(5)
                .panel(panel)
                .build();
        updateSidePanel();

        // Start raid state detection
        clientThread.invoke(() -> {
            raidState.startUp();
        });

        // Reload the panel with all loaded mistakes
        panel.reload();
    }

    @Override
    protected void shutDown() throws Exception {
        // Clear all state
        raidState.shutDown();
        mistakeDetectorManager.shutdown();

        // Remove UI
        clientToolbar.removeNavigation(navButton);
        panel = null;
    }

    // This should run *after* all detectors have handled the GameTick.
    @Subscribe(priority = -1)
    public void onGameTick(GameTick event) {
        if (!raidState.isInRaid()) return;

        // Try detecting all possible mistakes for this GameTick
        detectAll();

        // Invoke post-processing method for detectors to get ready for the next GameTick
        afterDetectAll();
    }

    private void detectAll() {
        for (Raider raider : raidState.getRaiders().values()) {
            if (raider != null) {
                detect(raider);
            }
        }
    }

    private void detect(@NonNull Raider raider) {
        List<TfaMistake> mistakes = mistakeDetectorManager.detectMistakes(raider);
        if (!mistakes.isEmpty()) {
            log.debug(client.getTickCount() + " Found mistakes for " + raider.getName() + " - " + mistakes);

            for (TfaMistake mistake : mistakes) {
                // Handle special logic for deaths
                if (mistake == TfaMistake.DEATH) {
                    raider.setDead(true);
                }

                addChatMessageForMistake(raider, mistake);
                addMistakeToOverlayPanel(raider, mistake);
            }
        }

        afterDetect(raider);
    }

    private void afterDetect(Raider raider) {
        raider.setPreviousWorldLocationForOverlay(raider.getPreviousWorldLocation());
        raider.setPreviousWorldLocation(raider.getCurrentWorldLocation());
    }

    private void afterDetectAll() {
        mistakeDetectorManager.afterDetect();
    }

    private void addChatMessageForMistake(Raider raider, TfaMistake mistake) {
        int mistakeCount = config.mistakeMessageStacking() == StackingBehavior.SAME_MISTAKES_ONLY
                ? panel.getCurrentMistakeCountForPlayer(raider.getName(), mistake)
                : panel.getCurrentTotalMistakeCountForPlayer(raider.getName());
        String msg = TfaMistake.getChatMessageForMistakeCount(config, mistake, mistakeCount);

        // Truncate message length to prevent overly-spammy messages taking up too much screen space
        if (msg.length() > TfaMistake.MAX_MESSAGE_LENGTH) {
            msg = msg.substring(0, TfaMistake.MAX_MESSAGE_LENGTH);
        }

        if (msg.isEmpty()) return;

        // Add to overhead text if config is enabled
        final Player player = raider.getPlayer();
        if (config.showMistakesOnOverheadText()) {
            String overheadText = msg;
            if (vengeanceTracker.didPopVengeance(raider)) {
                overheadText = VengeanceTracker.VENGEANCE_TEXT + " " + overheadText;
            }
            player.setOverheadText(overheadText);
            player.setOverheadCycle(CYCLES_FOR_OVERHEAD_TEXT);
        }

        // Add to chat box if config is enabled
        if (config.showMistakesInChat()) {
            client.addChatMessage(ChatMessageType.PUBLICCHAT, player.getName(), msg, null);
        }
    }

    private void addMistakeToOverlayPanel(Raider raider, TfaMistake mistake) {
        SwingUtilities.invokeLater(() -> panel.addMistakeForPlayer(raider.getName(), mistake));
    }

    @Subscribe
    public void onInRaidChanged(InRaidChanged e) {
        if (e.isInRaid()) {
            log.debug("Starting detectors");
            mistakeDetectorManager.startup();
        } else {
            log.debug("Shutting down detectors");
            mistakeDetectorManager.shutdown();
        }
    }

    @Subscribe
    public void onRaidEntered(RaidEntered event) {
        panel.newRaid(event.getRaiderNames());
    }

    private void updateSidePanel() {
        if (config.showSidePanel()) {
            clientToolbar.addNavigation(navButton);
        } else {
            clientToolbar.removeNavigation(navButton);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!CONFIG_GROUP.equals(event.getGroup())) return;

        if (SHOW_SIDE_PANEL_CONFIG_KEY.equals(event.getKey())) {
            updateSidePanel();
        }
    }

    @Provides
    TfaMistakeTrackerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TfaMistakeTrackerConfig.class);
    }
}
