package com.tfamistaketracker.detector;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.tfamistaketracker.RaidRoom;
import com.tfamistaketracker.RaidState;
import com.tfamistaketracker.Raider;
import com.tfamistaketracker.TfaMistake;
import com.tfamistaketracker.detector.boss.ArchiveGuardianDetector;
import com.tfamistaketracker.detector.boss.BorkDetector;
import com.tfamistaketracker.detector.boss.DreadnothDetector;
import com.tfamistaketracker.detector.boss.OcellusP1Detector;
import com.tfamistaketracker.detector.boss.OcellusP2Detector;
import com.tfamistaketracker.detector.boss.OcellusP3Detector;
import com.tfamistaketracker.detector.boss.XarthDetector;
import com.tfamistaketracker.detector.death.DeathDetector;
import com.tfamistaketracker.detector.tracker.BaseRaidTracker;
import com.tfamistaketracker.detector.tracker.VengeanceTracker;
import com.tfamistaketracker.events.RaidRoomChanged;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

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

    // How many of each NPC id that some detector requires are currently spawned
    private final Multiset<Integer> requiredNpcCounts = HashMultiset.create();

    @Inject
    public MistakeDetectorManager(Client client,
                                  EventBus eventBus,
                                  RaidState raidState,
                                  VengeanceTracker vengeanceTracker,
                                  ArchiveGuardianDetector archiveGuardianDetector,
                                  BorkDetector borkDetector,
                                  DreadnothDetector dreadnothDetector,
                                  XarthDetector xarthDetector,
                                  OcellusP1Detector ocellusP1Detector,
                                  OcellusP2Detector ocellusP2Detector,
                                  OcellusP3Detector ocellusP3Detector,
                                  DeathDetector deathDetector) {
        this.raidTrackers = Arrays.asList(vengeanceTracker);

        // Order matters, since it's last write wins for which mistake gets put on overhead text. Death should be last.
        this.mistakeDetectors = new ArrayList<>(Arrays.asList(
                archiveGuardianDetector,
                borkDetector,
                dreadnothDetector,
                xarthDetector,
                ocellusP1Detector,
                ocellusP2Detector,
                ocellusP3Detector,
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

        requiredNpcCounts.clear();
        for (NPC npc : client.getTopLevelWorldView().npcs()) {
            addNpc(npc.getId());
        }

        // The first room is entered before the manager starts listening for room changes
        refreshDetectors(raidstate.getCurrentRoom());
    }

    public void shutdown() {
        mistakeDetectors.forEach(BaseMistakeDetector::shutdown);
        // Don't clear mistakeDetectors or else we can't get them back.

        raidTrackers.forEach(BaseRaidTracker::shutdown);
        requiredNpcCounts.clear();

        eventBus.unregister(this);
        started = false;
    }

    public List<TfaMistake> detectMistakes(@NonNull Raider raider) {
        if (!started) return Collections.emptyList();

        List<TfaMistake> mistakes = new ArrayList<>();
        for (BaseMistakeDetector mistakeDetector : mistakeDetectors) {
            if (mistakeDetector.isStarted() && mistakeDetector.isDetectingMistakes() && !raider.isDead()) {
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
            if (mistakeDetector.isStarted() && mistakeDetector.isDetectingMistakes()) {
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
    }

    @Subscribe
    public void onRaidRoomChanged(RaidRoomChanged event) {
        refreshDetectors(event.getNewRaidRoom());
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        if (addNpc(event.getNpc().getId())) {
            refreshDetectors(raidstate.getCurrentRoom());
        }
    }

    @Subscribe
    public void onNpcChanged(NpcChanged event) {
        boolean removed = event.getOld() != null && removeNpc(event.getOld().getId());
        if (addNpc(event.getNpc().getId()) || removed) {
            refreshDetectors(raidstate.getCurrentRoom());
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        if (removeNpc(event.getNpc().getId())) {
            refreshDetectors(raidstate.getCurrentRoom());
        }
    }

    private boolean addNpc(int npcId) {
        if (!isRequiredNpc(npcId)) return false;
        requiredNpcCounts.add(npcId);
        return true;
    }

    private boolean removeNpc(int npcId) {
        if (!isRequiredNpc(npcId)) return false;
        requiredNpcCounts.remove(npcId);
        return true;
    }

    private boolean isRequiredNpc(int npcId) {
        return mistakeDetectors.stream().anyMatch(d -> d.getRequiredNpcId() != null && d.getRequiredNpcId() == npcId);
    }

    private void refreshDetectors(RaidRoom room) {
        for (BaseMistakeDetector detector : mistakeDetectors) {
            if (shouldBeStarted(detector, room)) {
                detector.startup();
            } else if (detector.isStarted()) {
                detector.shutdown();
            }
        }
    }

    private boolean shouldBeStarted(BaseMistakeDetector detector, RaidRoom room) {
        // A null room means *all* rooms
        if (detector.getRaidRoom() != null && detector.getRaidRoom() != room) return false;
        return detector.getRequiredNpcId() == null || requiredNpcCounts.contains(detector.getRequiredNpcId());
    }

}
