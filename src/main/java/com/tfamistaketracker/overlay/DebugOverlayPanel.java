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
import java.util.Set;
import java.util.stream.Collectors;

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

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Region")
                .right(getRegionText())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Room")
                .right(getRoomText())
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

        Set<String> raiderNames = raidState.getRaiderNames();
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Raiders: " + raidState.getRaiders().size() + "/" + raiderNames.size())
                .color(raidState.getRaiders().isEmpty() ? Color.RED : Color.GREEN)
                .build());

        raiderNames.forEach(name -> {
            boolean loaded = raidState.getRaiders().containsKey(name);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(name)
                    .right(loaded ? "loaded" : "missing")
                    .rightColor(loaded ? Color.GREEN : Color.RED)
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

        String started = mistakeDetectorManager.getMistakeDetectors().stream()
                .filter(BaseMistakeDetector::isStarted)
                .map(d -> d.getClass().getSimpleName().replace("Detector", ""))
                .collect(Collectors.joining(", "));
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Started")
                .right(started.isEmpty() ? "none" : started)
                .build());

        return super.render(graphics);
    }

    private String getRoomText() {
        if (raidState.getCurrentRoom() != null) {
            return raidState.getCurrentRoom().name();
        }
        return raidState.isInRaid() ? "unknown" : "null";
    }

    private String getRegionText() {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) return "-1";

        LocalPoint localPoint = localPlayer.getLocalLocation();
        if (localPoint == null) return "-1";

        WorldPoint location = WorldPoint.fromLocalInstance(client, localPoint);
        return location.getRegionID() + " p" + location.getPlane();
    }

    private void renderMistakeDetector(String name, boolean isOn) {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(name)
                .right(isOn ? "ON" : "OFF")
                .rightColor(isOn ? Color.GREEN : Color.RED)
                .build());
    }
}
