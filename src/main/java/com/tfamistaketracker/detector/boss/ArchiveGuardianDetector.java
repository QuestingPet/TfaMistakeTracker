package com.tfamistaketracker.detector.boss;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.tfamistaketracker.RaidRoom;
import com.tfamistaketracker.Raider;
import com.tfamistaketracker.TfaMistake;
import com.tfamistaketracker.detector.BaseMistakeDetector;
import com.tfamistaketracker.detector.tracker.DelayedHitTilesTracker;
import com.tfamistaketracker.detector.tracker.InstantHitTilesTracker;
import com.tfamistaketracker.detector.tracker.OverheadTracker;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tfamistaketracker.RaidRoom.ARCHIVE_GUARDIAN;
import static com.tfamistaketracker.TfaMistake.ARCHIVE_GUARDIAN_MELEE;
import static com.tfamistaketracker.TfaMistake.ARCHIVE_GUARDIAN_MUSHROOM;
import static com.tfamistaketracker.TfaMistake.ARCHIVE_GUARDIAN_RANGE;
import static com.tfamistaketracker.TfaMistake.ARCHIVE_GUARDIAN_FALL;
import static com.tfamistaketracker.TfaMistake.ARCHIVE_GUARDIAN_VINE;

/**
 * The Archive Guardian alternates between flying and grounded. When he drops down, he damages anyone under him or
 * within melee distance. While flying, he melees anyone under him.
 * <p>
 * Mushrooms explode over a 3x3 area when someone gets close, poisoning anyone still in it a tick later. Vines hit
 * everything adjacent to them on the tick they swipe.
 */
@Slf4j
@Singleton
public class ArchiveGuardianDetector extends BaseMistakeDetector {

    private static final Set<Integer> GUARDIAN_IDS = ImmutableSet.of(NpcID.TFA_GUARDIAN,
            NpcID.TFA_GUARDIAN_UNSHIELDED);

    private static final Map<Integer, OverheadTracker.Attack> RANGE_ATTACKS = ImmutableMap.of(
            SpotanimID.VFX_GUARDIAN_RANGED_PROJANIM01,
            OverheadTracker.Attack.of(HeadIcon.RANGED, OverheadTracker.PrayerCheck.ANIMATION));

    private static final int FALL_DISTANCE = 1;
    private static final int VINE_DISTANCE = 1;
    private static final int MUSHROOM_DISTANCE = 1;
    private static final int MELEE_HIT_DELAY_IN_TICKS = 1;
    private static final int MUSHROOM_HIT_DELAY_IN_TICKS = 1;

    // Overheads are only final on GameTick, since NPC animations can fire before player updates within a tick
    private NPC rangeAttacker;
    // Nothing in the room hurts once the guardian starts dying
    private boolean isGuardianDead;

    private final InstantHitTilesTracker fallHitTiles = new InstantHitTilesTracker();
    private final DelayedHitTilesTracker meleeHitTiles = new DelayedHitTilesTracker();
    private final OverheadTracker rangeOverheadTracker = new OverheadTracker(RANGE_ATTACKS);
    private final DelayedHitTilesTracker mushroomHitTiles = new DelayedHitTilesTracker();
    private final InstantHitTilesTracker vineHitTiles = new InstantHitTilesTracker();

    public ArchiveGuardianDetector() {
        rangeAttacker = null;
        isGuardianDead = false;
    }

    @Override
    public void cleanup() {
        isGuardianDead = false;
        clearTrackers();
    }

    private void clearTrackers() {
        rangeAttacker = null;
        fallHitTiles.clear();
        meleeHitTiles.clear();
        rangeOverheadTracker.clear();
        mushroomHitTiles.clear();
        vineHitTiles.clear();
    }

    @Override
    public RaidRoom getRaidRoom() {
        return ARCHIVE_GUARDIAN;
    }

    @Override
    public List<TfaMistake> detectMistakes(@NonNull Raider raider) {
        List<TfaMistake> mistakes = new ArrayList<>();
        if (isGuardianDead) {
            return mistakes;
        }

        if (fallHitTiles.getActiveHitTiles().contains(raider.getPreviousWorldLocation())) {
            mistakes.add(ARCHIVE_GUARDIAN_FALL);
        }

        if (meleeHitTiles.getActiveHitTiles().contains(raider.getPreviousWorldLocation()) &&
                !vengeanceTracker.didPopVengeance(raider)) {
            mistakes.add(ARCHIVE_GUARDIAN_MELEE);
        }

        if (isRangeHit(raider)) {
            mistakes.add(ARCHIVE_GUARDIAN_RANGE);
        }

        if (mushroomHitTiles.getActiveHitTiles().contains(raider.getPreviousWorldLocation())) {
            mistakes.add(ARCHIVE_GUARDIAN_MUSHROOM);
        }

        if (vineHitTiles.getActiveHitTiles().contains(raider.getPreviousWorldLocation())) {
            mistakes.add(ARCHIVE_GUARDIAN_VINE);
        }

        return mistakes;
    }

    @Override
    public void afterDetect() {
    }

    private boolean isRangeHit(Raider raider) {
        if (vengeanceTracker.didPopVengeance(raider)) {
            return false;
        }

        return rangeOverheadTracker.didMissPrayer(raider);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        fallHitTiles.onGameTick(client.getTickCount());
        meleeHitTiles.onGameTick(client.getTickCount());
        rangeOverheadTracker.onGameTick(client.getTickCount());
        if (rangeAttacker != null) {
            rangeOverheadTracker.snapshotOverheads(rangeAttacker, raidState.getRaiders().values());
        }
        rangeAttacker = null;
        mushroomHitTiles.onGameTick(client.getTickCount());
        vineHitTiles.onGameTick(client.getTickCount());
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        Actor actor = event.getActor();
        if (!(actor instanceof NPC) || isGuardianDead) return;

        NPC npc = (NPC) actor;
        if (GUARDIAN_IDS.contains(npc.getId())) {
            if (npc.getAnimation() == AnimationID.NPC_ARCHIVE_GUARDIAN_DEATH01) {
                onGuardianDeath();
            } else if (npc.getAnimation() == AnimationID.NPC_ARCHIVE_GUARDIAN_IDLE_TO_GROUND01) {
                computeTilesAround(npc.getWorldArea(), FALL_DISTANCE).forEach(fallHitTiles::add);
            } else if (npc.getAnimation() == AnimationID.NPC_ARCHIVE_GUARDIAN_RANGE_ATTACK01) {
                rangeAttacker = npc;
            } else if (npc.getAnimation() == AnimationID.NPC_ARCHIVE_GUARDIAN_MELEE_ATTACK01) {
                meleeHitTiles.putAll(client.getTickCount() + MELEE_HIT_DELAY_IN_TICKS,
                        computeTilesAround(npc.getWorldArea(), 0));
            }
        } else if (npc.getId() == NpcID.ARCHIVE_GUARDIAN_TENTACLE &&
                npc.getAnimation() == AnimationID.NPC_GUARDIAN_ARCHIVE_TENTACLE_ATTACK01) {
            computeTilesAround(actor.getWorldArea(), VINE_DISTANCE).forEach(vineHitTiles::add);
        }
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        if (!RANGE_ATTACKS.containsKey(event.getProjectile().getId()) || isGuardianDead) return;

        rangeOverheadTracker.trackProjectile(event, client.getTickCount());
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        if (event.getGameObject().getId() == ObjectID.TFA_GUARDIAN_MUSHROOM_DESPAWN && !isGuardianDead) {
            mushroomHitTiles.putAll(client.getTickCount() + MUSHROOM_HIT_DELAY_IN_TICKS,
                    computeTilesAround(event.getGameObject().getWorldLocation(), MUSHROOM_DISTANCE));
        }
    }

    @Subscribe
    public void onNpcChanged(NpcChanged event) {
        if (event.getNpc().getId() == NpcID.TFA_GUARDIAN_DEAD) {
            onGuardianDeath();
        }
    }

    private void onGuardianDeath() {
        isGuardianDead = true;
        clearTrackers();
    }
}
