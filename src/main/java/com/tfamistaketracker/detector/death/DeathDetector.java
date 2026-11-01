package com.tfamistaketracker.detector.death;

import com.tfamistaketracker.RaidRoom;
import com.tfamistaketracker.Raider;
import com.tfamistaketracker.TfaMistake;
import com.tfamistaketracker.detector.BaseMistakeDetector;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Player;
import net.runelite.api.events.ActorDeath;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.tfamistaketracker.TfaMistake.DEATH;

/**
 * Track deaths for each player in the raid using the {@link ActorDeath} event.
 */
@Slf4j
@Singleton
public class DeathDetector extends BaseMistakeDetector {

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
