package com.tfamistaketracker.detector.boss;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.tfamistaketracker.RaidRoom;
import com.tfamistaketracker.Raider;
import com.tfamistaketracker.TfaMistake;
import com.tfamistaketracker.detector.BaseMistakeDetector;
import com.tfamistaketracker.detector.tracker.DelayedObjectsTracker;
import com.tfamistaketracker.detector.tracker.OverheadTracker;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tfamistaketracker.RaidRoom.ARCHIVE_GUARDIAN;
import static com.tfamistaketracker.TfaMistake.ARCHIVE_GUARDIAN_MELEE;
import static com.tfamistaketracker.TfaMistake.ARCHIVE_GUARDIAN_MUSHROOM;
import static com.tfamistaketracker.TfaMistake.ARCHIVE_GUARDIAN_RANGE;
import static com.tfamistaketracker.TfaMistake.ARCHIVE_GUARDIAN_SLAM;
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

    private static final Map<Integer, HeadIcon> RANGE_ATTACKS = ImmutableMap.of(
            SpotanimID.VFX_GUARDIAN_RANGED_PROJANIM01, HeadIcon.RANGED);

    private static final int SLAM_DISTANCE = 1;
    private static final int MUSHROOM_DISTANCE = 1;
    private static final int VINE_DISTANCE = 1;
    private static final int MELEE_HIT_DELAY_IN_TICKS = 1;

    private NPC guardian;

    // The guardian's footprint on the tick he dropped down, or null
    private WorldArea slamArea;

    private final DelayedObjectsTracker<WorldArea> meleeAreas = new DelayedObjectsTracker<>();
    private final OverheadTracker rangeOverheadTracker = new OverheadTracker(RANGE_ATTACKS);

    // Mushrooms poison a tick after they explode
    private final Set<WorldPoint> mushroomsExploding;
    private final Set<WorldPoint> mushroomsExploded;

    private final Set<WorldPoint> vineAttackTiles;

    public ArchiveGuardianDetector() {
        guardian = null;
        slamArea = null;
        mushroomsExploding = new HashSet<>();
        mushroomsExploded = new HashSet<>();
        vineAttackTiles = new HashSet<>();
    }

    @Override
    public void cleanup() {
        guardian = null;
        slamArea = null;
        meleeAreas.clear();
        rangeOverheadTracker.clear();
        mushroomsExploding.clear();
        mushroomsExploded.clear();
        vineAttackTiles.clear();
    }

    @Override
    public RaidRoom getRaidRoom() {
        return ARCHIVE_GUARDIAN;
    }

    @Override
    public List<TfaMistake> detectMistakes(@NonNull Raider raider) {
        List<TfaMistake> mistakes = new ArrayList<>();

        if (isSlamHit(raider)) {
            mistakes.add(ARCHIVE_GUARDIAN_SLAM);
        }

        if (isMeleeHit(raider)) {
            mistakes.add(ARCHIVE_GUARDIAN_MELEE);
        }

        if (isRangeHit(raider)) {
            mistakes.add(ARCHIVE_GUARDIAN_RANGE);
        }

        if (isWithinDistance(raider, mushroomsExploded, MUSHROOM_DISTANCE)) {
            mistakes.add(ARCHIVE_GUARDIAN_MUSHROOM);
        }

        if (isWithinDistance(raider, vineAttackTiles, VINE_DISTANCE)) {
            mistakes.add(ARCHIVE_GUARDIAN_VINE);
        }

        return mistakes;
    }

    @Override
    public void afterDetect() {
        slamArea = null;

        mushroomsExploded.clear();
        mushroomsExploded.addAll(mushroomsExploding);
        mushroomsExploding.clear();

        vineAttackTiles.clear();
    }

    private boolean isSlamHit(Raider raider) {
        WorldPoint location = raider.getPreviousWorldLocation();
        return slamArea != null && location != null && slamArea.distanceTo(location) <= SLAM_DISTANCE;
    }

    private boolean isRangeHit(Raider raider) {
        if (vengeanceTracker.didPopVengeance(raider)) {
            return false;
        }

        return rangeOverheadTracker.getActiveProjectileForRaider(raider) != null &&
                rangeOverheadTracker.didMissPrayer(raider);
    }

    private boolean isMeleeHit(Raider raider) {
        WorldPoint location = raider.getPreviousWorldLocation();
        if (location == null) return false;

        for (WorldArea area : meleeAreas.getActiveObjects()) {
            if (area.contains(location)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWithinDistance(Raider raider, Set<WorldPoint> tiles, int distance) {
        WorldPoint location = raider.getPreviousWorldLocation();
        if (location == null) return false;

        for (WorldPoint tile : tiles) {
            if (tile.getPlane() == location.getPlane() && location.distanceTo2D(tile) <= distance) {
                return true;
            }
        }
        return false;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        meleeAreas.onGameTick(client.getTickCount());
        rangeOverheadTracker.onGameTick(client.getTickCount());
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        Actor actor = event.getActor();
        if (!(actor instanceof NPC)) return;

        if (actor == guardian) {
            if (guardian.getAnimation() == AnimationID.NPC_ARCHIVE_GUARDIAN_IDLE_TO_GROUND01) {
                slamArea = guardian.getWorldArea();
            } else if (guardian.getAnimation() == AnimationID.NPC_ARCHIVE_GUARDIAN_MELEE_ATTACK01) {
                meleeAreas.put(client.getTickCount() + MELEE_HIT_DELAY_IN_TICKS, guardian.getWorldArea());
            }
        } else if (((NPC) actor).getId() == NpcID.ARCHIVE_GUARDIAN_TENTACLE &&
                actor.getAnimation() == AnimationID.NPC_GUARDIAN_ARCHIVE_TENTACLE_ATTACK01) {
            vineAttackTiles.add(actor.getWorldLocation());
        }
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        if (!RANGE_ATTACKS.containsKey(event.getProjectile().getId())) return;

        rangeOverheadTracker.trackProjectile(event, getActivationTick(event.getProjectile()));
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        if (event.getGameObject().getId() == ObjectID.TFA_GUARDIAN_MUSHROOM_DESPAWN) {
            mushroomsExploding.add(event.getGameObject().getWorldLocation());
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        if (GUARDIAN_IDS.contains(event.getNpc().getId())) {
            guardian = event.getNpc();
        }
    }

    @Subscribe
    public void onNpcChanged(NpcChanged event) {
        if (GUARDIAN_IDS.contains(event.getNpc().getId())) {
            guardian = event.getNpc();
        } else if (event.getNpc() == guardian) {
            // Changed to its dead form
            guardian = null;
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        if (event.getNpc() == guardian) {
            guardian = null;
        }
    }
}
