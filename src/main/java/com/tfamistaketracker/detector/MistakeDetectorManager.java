package com.tfamistaketracker.detector;

import com.google.common.annotations.VisibleForTesting;
import com.tfamistaketracker.RaidState;
import com.tfamistaketracker.Raider;
import com.tfamistaketracker.TfaMistake;
import com.tfamistaketracker.detector.boss.ArchiveGuardianDetector;
import com.tfamistaketracker.detector.boss.BorkDetector;
import com.tfamistaketracker.detector.boss.DreadnothDetector;
import com.tfamistaketracker.detector.death.DeathDetector;
import com.tfamistaketracker.detector.tracker.BaseRaidTracker;
import com.tfamistaketracker.detector.tracker.VengeanceTracker;
import com.tfamistaketracker.events.DetectorErrored;
import com.tfamistaketracker.events.RaidRoomChanged;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
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

    private static final String WIPE_GAME_MESSAGE = "You failed to survive the Fractured Archive";

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

    // DEBUG ONLY -- shown on the debug overlay, remove before publishing
    @Getter
    private int errorCount;

    @Getter
    private DetectorErrored lastError;

    @Inject
    public MistakeDetectorManager(Client client,
                                  EventBus eventBus,
                                  RaidState raidState,
                                  VengeanceTracker vengeanceTracker,
                                  ArchiveGuardianDetector archiveGuardianDetector,
                                  BorkDetector borkDetector,
                                  DreadnothDetector dreadnothDetector,
                                  DeathDetector deathDetector) {
        this.raidTrackers = Arrays.asList(vengeanceTracker);

        // Order matters, since it's last write wins for which mistake gets put on overhead text. Death should be last.
        this.mistakeDetectors = new ArrayList<>(Arrays.asList(
                archiveGuardianDetector,
                borkDetector,
                dreadnothDetector,
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

        // Startup any detectors that should be active in *all* rooms, or in the current room. The first room is entered
        // before the manager starts listening for room changes.
        mistakeDetectors.stream()
                .filter(d -> d.getRaidRoom() == null || d.getRaidRoom() == raidstate.getCurrentRoom())
                .forEach(BaseMistakeDetector::startup);
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
                try {
                    mistakes.addAll(mistakeDetector.detectMistakes(raider));
                } catch (RuntimeException e) {
                    onError(mistakeDetector, e);
                }
            }
        }

        return mistakes;
    }

    public void afterDetect() {
        if (!started) return;

        for (BaseRaidTracker raidTracker : raidTrackers) {
            try {
                raidTracker.afterDetect();
            } catch (RuntimeException e) {
                onError(raidTracker, e);
            }
        }

        for (BaseMistakeDetector mistakeDetector : mistakeDetectors) {
            if (mistakeDetector.isDetectingMistakes()) {
                try {
                    mistakeDetector.afterDetect();
                } catch (RuntimeException e) {
                    onError(mistakeDetector, e);
                }
            }
        }
    }

    // Catch per detector so one broken detector can't stop the others, or the raiders' locations from updating
    private void onError(Object source, RuntimeException e) {
        log.warn("{} threw an exception", source.getClass().getSimpleName(), e);
        errorCount++;
        lastError = new DetectorErrored(client.getTickCount(), source.getClass().getSimpleName(), e);
        eventBus.post(lastError);
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

    // DEBUG ONLY -- uses reflection for hotswap. Remove before publishing.
    @SneakyThrows
    @VisibleForTesting
    public void reset() {
        for (int i = 0; i < mistakeDetectors.size(); i++) {
            BaseMistakeDetector detector = mistakeDetectors.get(i);
            try {
                detector.shutdown();
            } catch (Exception e) {
                log.error("Failed to shutdown old detector while resetting state", e);
                // Continue anyway
            }
            BaseMistakeDetector newInstance = detector.getClass().getDeclaredConstructor().newInstance();
            newInstance.setClient(client);
            newInstance.setEventBus(eventBus);
            newInstance.setRaidState(raidstate);
            newInstance.setVengeanceTracker(vengeanceTracker);
            if (started &&
                    (newInstance.getRaidRoom() == null || newInstance.getRaidRoom() == raidstate.getCurrentRoom())) {
                newInstance.startup();
            }
            mistakeDetectors.set(i, newInstance);
        }
    }

    // DEBUG ONLY -- remove before publishing
    @VisibleForTesting
    public <T extends BaseMistakeDetector> T getMistakeDetector(Class<T> clazz) {
        for (BaseMistakeDetector detector : mistakeDetectors) {
            if (detector.getClass().equals(clazz)) {
                return clazz.cast(detector);
            }
        }

        return null;
    }
}
