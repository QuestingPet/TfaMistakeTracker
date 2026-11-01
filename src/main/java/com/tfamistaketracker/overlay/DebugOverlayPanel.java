package com.tfamistaketracker.overlay;

import com.tfamistaketracker.RaidState;
import com.tfamistaketracker.TfaMistakeTrackerConfig;
import com.tfamistaketracker.TfaMistakeTrackerPlugin;
import com.tfamistaketracker.debug.EventLogger;
import com.tfamistaketracker.detector.BaseMistakeDetector;
import com.tfamistaketracker.detector.MistakeDetectorManager;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import javax.inject.Named;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

/**
 * This is for testing with a visual aid
 */
public class DebugOverlayPanel extends OverlayPanel {

    private final Client client;
    private final RaidState raidState;
    private final TfaMistakeTrackerConfig config;

    private final MistakeDetectorManager mistakeDetectorManager;
    private final EventLogger eventLogger;

    private final boolean developerMode;

    @Inject
    public DebugOverlayPanel(Client client, TfaMistakeTrackerPlugin plugin,
                             RaidState raidState,
                             TfaMistakeTrackerConfig config,
                             MistakeDetectorManager mistakeDetectorManager,
                             EventLogger eventLogger,
                             @Named("developerMode") boolean developerMode) {
        super(plugin);
        setPosition(OverlayPosition.TOP_RIGHT);
        setPriority(PRIORITY_MED);

        this.client = client;
        this.raidState = raidState;
        this.config = config;
        this.mistakeDetectorManager = mistakeDetectorManager;
        this.eventLogger = eventLogger;
        this.developerMode = developerMode;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!developerMode) return null;
        if (!config.debugMode()) return null;

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Game Tick: " + client.getTickCount())
                .build());

        // Region of the local player, used to find the region IDs for each RaidRoom
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Region")
                .right(String.valueOf(getRegion()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Room")
                .right(String.valueOf(raidState.getCurrentRoom()))
                .build());

        if (raidState.isInRaid()) {
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("In Raid")
                    .color(Color.GREEN)
                    .build());
        } else {
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("NOT in raid")
                    .color(Color.RED)
                    .build());
        }

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Raiders:")
                .color(raidState.getRaiders().isEmpty() ? Color.RED : Color.GREEN)
                .build());

        raidState.getRaiders().keySet().forEach(name -> {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(name)
                    .build());
        });

        // Event logger state
        boolean logging = eventLogger.isLogging();
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Event Logger")
                .right(logging ? "ON" : "OFF")
                .rightColor(logging ? Color.GREEN : Color.RED)
                .build());
        if (logging) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Last Mark")
                    .right("#" + eventLogger.getLastMarker())
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(eventLogger.getLogFilePath())
                    .build());
        }

        // Add all mistake detectors
        renderMistakeDetector(mistakeDetectorManager.getClass().getSimpleName(),
                mistakeDetectorManager.isStarted());
        for (BaseMistakeDetector mistakeDetector : mistakeDetectorManager.getMistakeDetectors()) {
            renderMistakeDetector(mistakeDetector.getClass().getSimpleName(), mistakeDetector.isDetectingMistakes());
        }

        return super.render(graphics);
    }

    private int getRegion() {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) return -1;

        LocalPoint localPoint = localPlayer.getLocalLocation();
        if (localPoint == null) return -1;

        return WorldPoint.fromLocalInstance(client, localPoint).getRegionID();
    }

    private void renderMistakeDetector(String name, boolean isOn) {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(name)
                .right(isOn ? "ON" : "OFF")
                .rightColor(isOn ? Color.GREEN : Color.RED)
                .build());
    }
}
