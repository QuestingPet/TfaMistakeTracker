package com.tfamistaketracker.detector.boss;

import com.google.common.collect.ImmutableMap;
import com.tfamistaketracker.RaidRoom;
import com.tfamistaketracker.Raider;
import com.tfamistaketracker.TfaMistake;
import com.tfamistaketracker.detector.BaseMistakeDetector;
import com.tfamistaketracker.detector.tracker.DelayedHitTilesTracker;
import com.tfamistaketracker.detector.tracker.OverheadTracker;
import com.tfamistaketracker.overlay.DebugTilesOverlay;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicsObjectCreated;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tfamistaketracker.RaidRoom.DREADNOTH;
import static com.tfamistaketracker.TfaMistake.DREADNOTH_BLOOD_POOL;
import static com.tfamistaketracker.TfaMistake.DREADNOTH_BLOOD_BALL;
import static com.tfamistaketracker.TfaMistake.DREADNOTH_INSIDE;
import static com.tfamistaketracker.TfaMistake.DREADNOTH_JELLY;
import static com.tfamistaketracker.TfaMistake.DREADNOTH_LANDED;
import static com.tfamistaketracker.TfaMistake.DREADNOTH_RANGE;
import static com.tfamistaketracker.TfaMistake.DREADNOTH_SLAM;

/**
 * Dreadnoth's jumps slam the whole room, which melee prayer blocks, except for anyone he lands on. His ranged
 * projectiles need range prayer when they hit, and his blood balls and dead jellies leave blood pools that hurt
 * anyone standing on them. Jellies melee whoever they're attacking unless they pray melee.
 */
@Slf4j
@Singleton
public class DreadnothDetector extends BaseMistakeDetector {

    private static final Map<Integer, OverheadTracker.Attack> RANGE_ATTACKS = ImmutableMap.of(
            SpotanimID.VFX_DREADNOTH_RANGED_PROJ01,
            OverheadTracker.Attack.of(HeadIcon.RANGED, OverheadTracker.PrayerCheck.HIT));

    private static final int JUMP_IN_PLACE_DURATION_IN_TICKS = 1;
    private static final int JUMP_DURATION_IN_TICKS = 3;
    private static final int LANDING_DISTANCE = 2;
    private static final int BLOOD_POOL_HIT_INTERVAL_IN_TICKS = 2;
    private static final int INSIDE_DELAY_AFTER_LANDING_IN_TICKS = 1;

    // Needed every tick for the footprint, and he spawns before the room is detected
    private NPC dreadnoth;
    private WorldArea previousDreadnothArea;
    private int landingTick;
    // His body stays in place after he dies, but standing in it is no longer a mistake
    private boolean isDreadnothDead;

    private final Set<Integer> slamTicks = new HashSet<>();
    private final Set<String> jellyTargets = new HashSet<>();
    private final OverheadTracker rangeOverheadTracker = new OverheadTracker(RANGE_ATTACKS);
    private final DelayedHitTilesTracker landingHitTiles = new DelayedHitTilesTracker();
    private final DelayedHitTilesTracker bloodBallHitTiles = new DelayedHitTilesTracker();
    private final Set<Projectile> trackedBloodBalls = new HashSet<>();
    // Blood pool tile -> tick it spawned. Pools don't hurt on the tick they spawn.
    private final Map<WorldPoint, Integer> bloodPools = new HashMap<>();
    // Raider name -> tick they were last hurt by a blood pool. Pools can't hurt the same raider on back-to-back ticks.
    private final Map<String, Integer> lastBloodPoolHitTicks = new HashMap<>();

    public DreadnothDetector() {
        dreadnoth = null;
        previousDreadnothArea = null;
        landingTick = -1;
        isDreadnothDead = false;
    }

    @Override
    public void startup() {
        super.startup();
        for (NPC npc : client.getTopLevelWorldView().npcs()) {
            if (npc.getId() == NpcID.TFA_JELLY_BOSS) {
                dreadnoth = npc;
            }
        }
    }

    @Override
    public void cleanup() {
        dreadnoth = null;
        previousDreadnothArea = null;
        landingTick = -1;
        isDreadnothDead = false;
        slamTicks.clear();
        jellyTargets.clear();
        rangeOverheadTracker.clear();
        landingHitTiles.clear();
        bloodBallHitTiles.clear();
        trackedBloodBalls.clear();
        bloodPools.clear();
        lastBloodPoolHitTicks.clear();
    }

    @Override
    public RaidRoom getRaidRoom() {
        return DREADNOTH;
    }

    @Override
    public List<TfaMistake> detectMistakes(@NonNull Raider raider) {
        List<TfaMistake> mistakes = new ArrayList<>();
        WorldPoint previousLocation = raider.getPreviousWorldLocation();

        boolean isLandedOn = landingHitTiles.getActiveHitTiles().contains(previousLocation);
        if (isLandedOn) {
            mistakes.add(DREADNOTH_LANDED);
        } else if (isSlamHit(raider)) {
            mistakes.add(DREADNOTH_SLAM);
        }

        if (isRangeHit(raider)) {
            mistakes.add(DREADNOTH_RANGE);
        }

        if (bloodBallHitTiles.getActiveHitTiles().contains(previousLocation)) {
            mistakes.add(DREADNOTH_BLOOD_BALL);
        }

        if (isBloodPoolHit(raider)) {
            mistakes.add(DREADNOTH_BLOOD_POOL);
        }

        if (jellyTargets.contains(raider.getName()) && raider.getPlayer().getOverheadIcon() != HeadIcon.MELEE) {
            mistakes.add(DREADNOTH_JELLY);
        }

        if (isInside(previousLocation)) {
            mistakes.add(DREADNOTH_INSIDE);
        }

        return mistakes;
    }

    @Override
    public void afterDetect() {
        slamTicks.remove(client.getTickCount());
        jellyTargets.clear();
        previousDreadnothArea = dreadnoth == null ? null : dreadnoth.getWorldArea();
    }

    // DEBUG ONLY -- remove before publishing
    @Override
    public void renderDebugTiles(DebugTilesOverlay overlay) {
        overlay.renderProjectiles(rangeOverheadTracker);
        overlay.renderHitTiles(landingHitTiles);
        overlay.renderHitTiles(bloodBallHitTiles);
        overlay.renderLastingTiles(bloodPools.keySet());
        if (previousDreadnothArea != null && client.getTickCount() > landingTick + INSIDE_DELAY_AFTER_LANDING_IN_TICKS &&
                !isDreadnothDead) {
            overlay.renderLastingTiles(previousDreadnothArea.toWorldPointList());
        }
    }

    private boolean isSlamHit(Raider raider) {
        if (!slamTicks.contains(client.getTickCount())) {
            return false;
        }

        if (vengeanceTracker.didPopVengeance(raider)) {
            return false;
        }

        return raider.getPlayer().getOverheadIcon() != HeadIcon.MELEE;
    }

    private boolean isRangeHit(Raider raider) {
        if (vengeanceTracker.didPopVengeance(raider)) {
            return false;
        }

        return rangeOverheadTracker.didMissPrayer(raider);
    }

    private boolean isBloodPoolHit(Raider raider) {
        WorldPoint location = raider.getPreviousWorldLocation();
        int tick = client.getTickCount();
        Integer spawnTick = bloodPools.get(location);
        if (spawnTick == null || spawnTick == tick) {
            return false;
        }

        Integer lastHitTick = lastBloodPoolHitTicks.get(raider.getName());
        if (lastHitTick != null && tick - lastHitTick < BLOOD_POOL_HIT_INTERVAL_IN_TICKS) {
            return false;
        }

        lastBloodPoolHitTicks.put(raider.getName(), tick);
        return true;
    }

    private boolean isInside(WorldPoint location) {
        // He's in the air from the start of a jump until he lands, and doesn't hurt anyone inside him until a tick later
        if (location == null || client.getTickCount() <= landingTick + INSIDE_DELAY_AFTER_LANDING_IN_TICKS ||
                isDreadnothDead) {
            return false;
        }

        return previousDreadnothArea != null && previousDreadnothArea.contains(location);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        rangeOverheadTracker.onGameTick(client.getTickCount());
        landingHitTiles.onGameTick(client.getTickCount());
        bloodBallHitTiles.onGameTick(client.getTickCount());
        trackedBloodBalls.removeIf(projectile -> projectile.getRemainingCycles() <= 0);
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        Actor actor = event.getActor();
        if (!(actor instanceof NPC)) return;

        NPC npc = (NPC) actor;
        if (npc.getId() == NpcID.TFA_JELLY_BOSS) {
            if (npc.getAnimation() == AnimationID.DREADNOTH_JUMP01) {
                onJump(JUMP_IN_PLACE_DURATION_IN_TICKS);
            } else if (npc.getAnimation() == AnimationID.DREADNOTH_JUMP02) {
                onJump(JUMP_DURATION_IN_TICKS);
            } else if (npc.getAnimation() == AnimationID.DREADNOTH_DEATH01) {
                isDreadnothDead = true;
            }
        } else if (npc.getId() == NpcID.TFA_JELLY_MINION && npc.getAnimation() == AnimationID.JELLY_ATTACK &&
                npc.getInteracting() instanceof Player) {
            jellyTargets.add(npc.getInteracting().getName());
        }
    }

    private void onJump(int durationInTicks) {
        int tick = client.getTickCount() + durationInTicks;
        slamTicks.add(tick);
        landingTick = Math.max(landingTick, tick);
    }

    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated event) {
        if (event.getGraphicsObject().getId() != SpotanimID.TFA_BOSS_JELLY_5X5_JUMP) return;

        landingHitTiles.putAll(client.getTickCount() + JUMP_DURATION_IN_TICKS,
                computeTilesAround(getWorldPoint(event.getGraphicsObject()), LANDING_DISTANCE));
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        Projectile projectile = event.getProjectile();
        if (RANGE_ATTACKS.containsKey(projectile.getId())) {
            rangeOverheadTracker.trackProjectile(event, client.getTickCount());
        } else if (projectile.getId() == SpotanimID.VFX_DREADNOTH_RANGED_PROJ02 &&
                trackedBloodBalls.add(projectile)) {
            bloodBallHitTiles.put(getActivationTick(projectile), projectile.getTargetPoint());
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        if (event.getGameObject().getId() == ObjectID.TFA_JELLY_BOSS_DAMAGE_TILE) {
            bloodPools.put(event.getGameObject().getWorldLocation(), client.getTickCount());
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        if (event.getGameObject().getId() == ObjectID.TFA_JELLY_BOSS_DAMAGE_TILE) {
            bloodPools.remove(event.getGameObject().getWorldLocation());
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        if (event.getNpc().getId() == NpcID.TFA_JELLY_BOSS) {
            dreadnoth = event.getNpc();
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        if (event.getNpc() == dreadnoth) {
            dreadnoth = null;
        }
    }
}
