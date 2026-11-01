package com.tfamistaketracker.detector;

import com.google.common.annotations.VisibleForTesting;
import com.tfamistaketracker.RaidState;
import com.tfamistaketracker.Raider;
import com.tfamistaketracker.TfaMistake;
import com.tfamistaketracker.detector.death.DeathDetector;
import com.tfamistaketracker.detector.tracker.BaseRaidTracker;
import com.tfamistaketracker.detector.tracker.VengeanceTracker;
import com.tfamistaketracker.events.RaidRoomChanged;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Manager for all the {@link BaseMistakeDetector}. It keeps all the detectors in memory in order to manage events.
 * <p>
 * All detectors initialized in the manager are responsible for determining when to start detecting mistakes.
 * The manager may call the startup() or shutdown() method on a detector at any time.
 * <p>
 * When the manager is on (started = true), then all other detectors are subscribed to the EventBus and
 * listening for events on when to turn themselves on/off. This will only be true while the player is in TFA.
 */
@Slf4j
@Singleton
public class MistakeDetectorManager {

    // TODO: Verify the TFA wipe game message. ToA used "Your party failed to complete the challenge"
    private static final String WIPE_GAME_MESSAGE = null;

    private final Client client;
    private final EventBus eventBus;
    private final RaidState raidstate;
    private final VengeanceTracker vengeanceTracker;

    @Getter
    private final List<BaseRaidTracker> raidTrackers;

    @Getter
    private final List<BaseMistakeDetector> mistakeDetectors;

    @Getter
    @VisibleForTesting
    private boolean started;

    @Inject
    public MistakeDetectorManager(Client client,
                                  EventBus eventBus,
                                  RaidState raidState,
                                  VengeanceTracker vengeanceTracker,
                                  DeathDetector deathDetector) {
        this.raidTrackers = Arrays.asList(vengeanceTracker);

        // Order matters, since it's last write wins for which mistake gets put on overhead text. Death should be last.
        this.mistakeDetectors = new ArrayList<>(Arrays.asList(
                deathDetector
        ));

        this.client = client;
        this.eventBus = eventBus;
        this.raidstate = raidState;
        this.vengeanceTracker = vengeanceTracker;
        this.started = false;
    }

    public void startup() {
        started = true;
        eventBus.register(this);

        // Startup all raid trackers
        raidTrackers.forEach(BaseRaidTracker::startup);

        // Startup any detectors that should be active in *all* rooms
        mistakeDetectors.stream().filter(d -> d.getRaidRoom() == null).forEach(BaseMistakeDetector::startup);
    }

    public void shutdown() {
        mistakeDetectors.forEach(BaseMistakeDetector::shutdown);
        // Don't clear mistakeDetectors or else we can't get them back.

        raidTrackers.forEach(BaseRaidTracker::shutdown);

        eventBus.unregister(this);
        started = false;
    }

    public List<TfaMistake> detectMistakes(@NonNull Raider raider) {
        if (!started) return Collections.emptyList();

        List<TfaMistake> mistakes = new ArrayList<>();
        for (BaseMistakeDetector mistakeDetector : mistakeDetectors) {
            if (mistakeDetector.isDetectingMistakes() && !raider.isDead()) {
                mistakes.addAll(mistakeDetector.detectMistakes(raider));
            }
        }

        return mistakes;
    }

    public void afterDetect() {
        if (!started) return;

        raidTrackers.forEach(BaseRaidTracker::afterDetect);

        for (BaseMistakeDetector mistakeDetector : mistakeDetectors) {
            if (mistakeDetector.isDetectingMistakes()) {
                mistakeDetector.afterDetect();
            }
        }
    }

    @Subscribe
    public void onRaidRoomChanged(RaidRoomChanged event) {
        // Detectors that run in *all* rooms do not need to handle these events
        mistakeDetectors.stream().filter(d -> d.getRaidRoom() != null).forEach(detector -> {
            if (detector.getRaidRoom() == event.getNewRaidRoom()) {
                detector.startup();
            } else if (detector.getRaidRoom() == event.getPrevRaidRoom()) {
                detector.shutdown();
            }
        });
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (WIPE_GAME_MESSAGE == null) return;
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

        String message = Text.removeTags(event.getMessage());
        if (message != null && message.startsWith(WIPE_GAME_MESSAGE)) {
            // If the team has wiped, all active detectors should reset state just in case.
            log.debug("Team wiped -- Resetting all active detectors");
            mistakeDetectors.stream()
                    .filter(BaseMistakeDetector::isDetectingMistakes)
                    .forEach(d -> {
                        d.shutdown();
                        d.startup();
                    });
        }
    }
}
