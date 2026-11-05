package com.tfamistaketracker.detector.death;

import com.tfamistaketracker.RaidRoom;
import com.tfamistaketracker.Raider;
import com.tfamistaketracker.TfaMistake;
import com.tfamistaketracker.detector.BaseMistakeDetector;
import com.google.common.collect.ImmutableMap;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Player;
import net.runelite.api.events.ActorDeath;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tfamistaketracker.TfaMistake.DEATH;
import static com.tfamistaketracker.TfaMistake.DEATH_ARCHIVE_GUARDIAN;
import static com.tfamistaketracker.TfaMistake.DEATH_BORK;
import static com.tfamistaketracker.TfaMistake.DEATH_DAGONHAI;
import static com.tfamistaketracker.TfaMistake.DEATH_DREADNOTH;
import static com.tfamistaketracker.TfaMistake.DEATH_EZEKIEL;
import static com.tfamistaketracker.TfaMistake.DEATH_MOIA;
import static com.tfamistaketracker.TfaMistake.DEATH_OCELLUS;
import static com.tfamistaketracker.TfaMistake.DEATH_XARTH;

/**
 * Track deaths for each player in the raid using the {@link ActorDeath} event. Also map each room to the room death.
 */
@Slf4j
@Singleton
public class DeathDetector extends BaseMistakeDetector {

    private static final Map<RaidRoom, TfaMistake> ROOM_DEATHS = ImmutableMap.<RaidRoom, TfaMistake>builder()
            .put(RaidRoom.ARCHIVE_GUARDIAN, DEATH_ARCHIVE_GUARDIAN)
            .put(RaidRoom.EZEKIEL, DEATH_EZEKIEL)
            .put(RaidRoom.BORK, DEATH_BORK)
            .put(RaidRoom.MOIA, DEATH_MOIA)
            .put(RaidRoom.DREADNOTH, DEATH_DREADNOTH)
            .put(RaidRoom.DAGONHAI, DEATH_DAGONHAI)
            .put(RaidRoom.XARTH, DEATH_XARTH)
            .put(RaidRoom.OCELLUS, DEATH_OCELLUS)
            .build();

    private final Set<String> raiderDeaths;

    public DeathDetector() {
        raiderDeaths = new HashSet<>();
    }

    @Override
    public void cleanup() {
        raiderDeaths.clear();
    }

    @Override
    public RaidRoom getRaidRoom() {
        return null; // null means *all* rooms
    }

    @Override
    public List<TfaMistake> detectMistakes(@NonNull Raider raider) {
        List<TfaMistake> mistakes = new ArrayList<>();

        if (raiderDeaths.contains(raider.getName())) {
            mistakes.add(DEATH);

            if (!ROOM_DEATHS.containsKey(raidState.getCurrentRoom())) {
                // Should never happen. If it does, log and return empty mistakes for this death
                log.error("Unknown room death: {}", raidState.getCurrentRoom());
                return Collections.emptyList();
            }

            mistakes.add(ROOM_DEATHS.get(raidState.getCurrentRoom()));
        }

        return mistakes;
    }

    @Override
    public void afterDetect() {
        raiderDeaths.clear();
    }

    @Subscribe
    public void onActorDeath(ActorDeath event) {
        if (!(event.getActor() instanceof Player)) return;

        if (raidState.isRaider(event.getActor()))
            raiderDeaths.add(event.getActor().getName());
    }
}
