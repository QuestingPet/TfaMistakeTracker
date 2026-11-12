package com.tfamistaketracker.detector.boss;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.tfamistaketracker.RaidRoom;
import com.tfamistaketracker.Raider;
import com.tfamistaketracker.TfaMistake;
import com.tfamistaketracker.detector.BaseMistakeDetector;
import com.tfamistaketracker.detector.tracker.InstantHitTilesTracker;
import com.tfamistaketracker.detector.tracker.OverheadTracker;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.HeadIcon;
import net.runelite.api.HitsplatID;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tfamistaketracker.RaidRoom.OCELLUS;
import static com.tfamistaketracker.TfaMistake.OCELLUS_P1_AUTO_ATTACK;
import static com.tfamistaketracker.TfaMistake.OCELLUS_P1_SLAM;

/**
 * In phase 1, Ocellus fires magic and ranged projectiles that need the matching prayer when they hit, and his slam
 * explodes the floor around him the tick after his explode animation.
 */
@Slf4j
@Singleton
public class OcellusP1Detector extends BaseMistakeDetector {

    private static final Map<Integer, OverheadTracker.Attack> AUTO_ATTACKS = ImmutableMap.of(
            SpotanimID.VFX_OCELLUS_WATER_ATTACK01_PROJANIM,
            OverheadTracker.Attack.of(HeadIcon.MAGIC, OverheadTracker.PrayerCheck.HIT),
            SpotanimID.VFX_OCELLUS_FIRE_ATTACK01_PROJANIM,
            OverheadTracker.Attack.of(HeadIcon.RANGED, OverheadTracker.PrayerCheck.HIT));

    private static final Set<Integer> SLAM_GRAPHICS_IDS = ImmutableSet.of(
            SpotanimID.VFX_OCELLUS_P1_EXPLODE_FLOOR,
            SpotanimID.VFX_OCELLUS_P1_EXPLODE_FLOOR_90,
            SpotanimID.VFX_OCELLUS_P1_EXPLODE_FLOOR_180,
            SpotanimID.VFX_OCELLUS_P1_EXPLODE_FLOOR_270);

    private final OverheadTracker autoAttackOverheadTracker = new OverheadTracker(AUTO_ATTACKS);
    // Early hits are decided a tick late, so vengeance from the tick they landed has to be remembered here
    private final Set<String> vengeanceThisTick = new HashSet<>();
    private final Set<String> vengeanceLastTick = new HashSet<>();
    private final InstantHitTilesTracker slamHitTiles = new InstantHitTilesTracker();

    @Override
    public void cleanup() {
        autoAttackOverheadTracker.clear();
        vengeanceThisTick.clear();
        vengeanceLastTick.clear();
        slamHitTiles.clear();
    }

    @Override
    public RaidRoom getRaidRoom() {
        return OCELLUS;
    }

    // Every phase is the same Ocellus NPC changing form, so the phase is tracked from his id
    @Override
    public Integer getRequiredNpcId() {
        return NpcID.TFA_OCELLUS_P1;
    }

    @Override
    public List<TfaMistake> detectMistakes(@NonNull Raider raider) {
        List<TfaMistake> mistakes = new ArrayList<>();
        if (vengeanceTracker.didPopVengeance(raider)) {
            vengeanceThisTick.add(raider.getName());
        }

        if (autoAttackOverheadTracker.didMissPrayer(raider) && !(autoAttackOverheadTracker.didHitEarly(raider) &&
                vengeanceLastTick.contains(raider.getName()))) {
            mistakes.add(OCELLUS_P1_AUTO_ATTACK);
        }

        if (slamHitTiles.getActiveHitTiles().contains(raider.getPreviousWorldLocation())) {
            mistakes.add(OCELLUS_P1_SLAM);
        }

        return mistakes;
    }

    @Override
    public void afterDetect() {
        vengeanceLastTick.clear();
        vengeanceLastTick.addAll(vengeanceThisTick);
        vengeanceThisTick.clear();
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        autoAttackOverheadTracker.onGameTick(client.getTickCount());
        slamHitTiles.onGameTick(client.getTickCount());
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        if (!AUTO_ATTACKS.containsKey(event.getProjectile().getId())) return;

        autoAttackOverheadTracker.trackProjectile(event, client.getTickCount());
    }

    // His projectiles often land a tick before their remaining cycles say
    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (!(event.getActor() instanceof Player)) return;

        int type = event.getHitsplat().getHitsplatType();
        if (type == HitsplatID.HEAL || type == HitsplatID.POISON || type == HitsplatID.VENOM ||
                type == HitsplatID.DISEASE || type == HitsplatID.BURN) return;

        autoAttackOverheadTracker.onHitsplatApplied(event.getActor(), client.getTickCount());
    }

    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated event) {
        if (!SLAM_GRAPHICS_IDS.contains(event.getGraphicsObject().getId())) return;

        slamHitTiles.add(getWorldPoint(event.getGraphicsObject()));
    }
}
