package com.tfamistaketracker.detector.boss;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.tfamistaketracker.RaidRoom;
import com.tfamistaketracker.Raider;
import com.tfamistaketracker.TfaMistake;
import com.tfamistaketracker.detector.BaseMistakeDetector;
import com.tfamistaketracker.detector.tracker.InstantHitTilesTracker;
import com.tfamistaketracker.detector.tracker.OverheadTracker;
import com.tfamistaketracker.overlay.DebugTilesOverlay;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicsObjectCreated;
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

import static com.tfamistaketracker.RaidRoom.BORK;
import static com.tfamistaketracker.TfaMistake.BORK_FIRE;
import static com.tfamistaketracker.TfaMistake.BORK_MELEE;
import static com.tfamistaketracker.TfaMistake.BORK_SMORKS_AA;
import static com.tfamistaketracker.TfaMistake.BORK_STOMP;

/**
 * Bork's stomp and melee both hit the tiles marked by wave dust graphics on the tick they appear. The stomp sends
 * several waves while he's stomping, and his melee hits twice. Melee prayer reduces the melee but not the stomp.
 * <p>
 * The Smorks fire ranged and magic projectiles that need the matching prayer when they hit. Their fireballs leave
 * fire patches that last until Bork dies and burn anyone standing on them.
 */
@Slf4j
@Singleton
public class BorkDetector extends BaseMistakeDetector {

    private static final Set<Integer> STOMP_ANIMATION_IDS = ImmutableSet.of(
            AnimationID.NPC_BORK_ENRAGED_STOMP_ATTACK_IN01,
            AnimationID.NPC_BORK_ENRAGED_STOMP_ATTACK01,
            AnimationID.NPC_BORK_ENRAGED_STOMP_ATTACK_CYCLE_AND_OUT01);

    private static final Map<Integer, OverheadTracker.Attack> SMORKS_ATTACKS = ImmutableMap.of(
            SpotanimID.VFX_SMORK_RANGE_ATTACK_PROJANIM01,
            OverheadTracker.Attack.of(HeadIcon.RANGED, OverheadTracker.PrayerCheck.HIT),
            SpotanimID.VFX_SMORK_MAGE_ATTACK_PROJANIM01,
            OverheadTracker.Attack.of(HeadIcon.MAGIC, OverheadTracker.PrayerCheck.HIT));

    // Bork spawns before the room is detected, so stomps are tracked from his animations instead of the NPC
    private boolean isStomping;

    private final InstantHitTilesTracker stompHitTiles = new InstantHitTilesTracker();
    private final InstantHitTilesTracker meleeHitTiles = new InstantHitTilesTracker();
    private final OverheadTracker smorksOverheadTracker = new OverheadTracker(SMORKS_ATTACKS);
    private final Set<WorldPoint> fireTiles = new HashSet<>();

    public BorkDetector() {
        isStomping = false;
    }

    @Override
    public void cleanup() {
        isStomping = false;
        stompHitTiles.clear();
        meleeHitTiles.clear();
        smorksOverheadTracker.clear();
        fireTiles.clear();
    }

    @Override
    public RaidRoom getRaidRoom() {
        return BORK;
    }

    @Override
    public List<TfaMistake> detectMistakes(@NonNull Raider raider) {
        List<TfaMistake> mistakes = new ArrayList<>();

        if (fireTiles.contains(raider.getPreviousWorldLocation())) {
            mistakes.add(BORK_FIRE);
        }

        if (stompHitTiles.getActiveHitTiles().contains(raider.getPreviousWorldLocation())) {
            mistakes.add(BORK_STOMP);
        }

        if (isMeleeHit(raider)) {
            mistakes.add(BORK_MELEE);
        }

        if (isSmorksHit(raider)) {
            mistakes.add(BORK_SMORKS_AA);
        }

        return mistakes;
    }

    @Override
    public void afterDetect() {
    }

    // DEBUG ONLY -- remove before publishing
    @Override
    public void renderDebugTiles(DebugTilesOverlay overlay) {
        overlay.renderHitTiles(stompHitTiles);
        overlay.renderHitTiles(meleeHitTiles);
        overlay.renderProjectiles(smorksOverheadTracker);
        overlay.renderLastingTiles(fireTiles);
    }

    private boolean isMeleeHit(Raider raider) {
        if (!meleeHitTiles.getActiveHitTiles().contains(raider.getPreviousWorldLocation())) {
            return false;
        }

        if (vengeanceTracker.didPopVengeance(raider)) {
            return false;
        }

        return raider.getPlayer().getOverheadIcon() != HeadIcon.MELEE;
    }

    private boolean isSmorksHit(Raider raider) {
        if (vengeanceTracker.didPopVengeance(raider)) {
            return false;
        }

        return smorksOverheadTracker.didMissPrayer(raider);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        stompHitTiles.onGameTick(client.getTickCount());
        meleeHitTiles.onGameTick(client.getTickCount());
        smorksOverheadTracker.onGameTick(client.getTickCount());
    }

    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated event) {
        if (event.getGraphicsObject().getId() != SpotanimID.TFA_WAVE_DUST_SHORT) return;

        WorldPoint tile = getWorldPoint(event.getGraphicsObject());
        if (isStomping) {
            stompHitTiles.add(tile);
        } else {
            meleeHitTiles.add(tile);
        }
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        if (!SMORKS_ATTACKS.containsKey(event.getProjectile().getId())) return;

        smorksOverheadTracker.trackProjectile(event, client.getTickCount());
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        if (event.getGameObject().getId() == ObjectID.TFA_BORK_RAGGED_TILE) {
            fireTiles.add(event.getGameObject().getWorldLocation());
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        if (event.getGameObject().getId() == ObjectID.TFA_BORK_RAGGED_TILE) {
            fireTiles.remove(event.getGameObject().getWorldLocation());
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (!(event.getActor() instanceof NPC) || ((NPC) event.getActor()).getId() != NpcID.TFA_BORK) return;

        isStomping = STOMP_ANIMATION_IDS.contains(event.getActor().getAnimation());
    }
}
