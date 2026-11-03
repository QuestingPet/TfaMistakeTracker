package com.tfamistaketracker.detector.boss;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.tfamistaketracker.RaidRoom;
import com.tfamistaketracker.Raider;
import com.tfamistaketracker.TfaMistake;
import com.tfamistaketracker.detector.BaseMistakeDetector;
import com.tfamistaketracker.detector.tracker.DelayedHitTilesTracker;
import com.tfamistaketracker.detector.tracker.InstantHitTilesTracker;
import com.tfamistaketracker.overlay.DebugTilesOverlay;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.GraphicsObject;
import net.runelite.api.GroundObject;
import net.runelite.api.HeadIcon;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.HitsplatApplied;
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

import static com.tfamistaketracker.RaidRoom.OCELLUS;
import static com.tfamistaketracker.TfaMistake.OCELLUS_P2_PURPLE_TILE;
import static com.tfamistaketracker.TfaMistake.OCELLUS_P3_AOE;
import static com.tfamistaketracker.TfaMistake.OCELLUS_P3_FALL;
import static com.tfamistaketracker.TfaMistake.OCELLUS_P3_INSOLENCE;
import static com.tfamistaketracker.TfaMistake.OCELLUS_P3_PARRY;
import static com.tfamistaketracker.TfaMistake.OCELLUS_P3_PRAYER_ATTACK;
import static com.tfamistaketracker.TfaMistake.OCELLUS_P3_RIFT_EXPLOSION;
import static com.tfamistaketracker.TfaMistake.OCELLUS_P3_UNTETHER;
import static com.tfamistaketracker.TfaMistake.OCELLUS_P3_VOID_EXPLOSION;
import static com.tfamistaketracker.TfaMistake.OCELLUS_P3_VOID_GRIEF;

/**
 * In phase 3, Ocellus slashes, drops void bombs that leave void floor behind, and explodes the arena after untethering:
 * first everything outside him, then under him a tick later. During his parry and his hybrid attack, raiders have to
 * pray correctly when the hits land, and rifts that aren't dealt with in time explode on everyone. After his range and
 * melee attacks, void explosions chase each raider until his next prayer attack. Only your own are visible, so they're
 * recognised by damage. Nothing counts once he starts dying.
 */
@Slf4j
@Singleton
public class OcellusP3Detector extends BaseMistakeDetector {

    private static final Set<Integer> VOID_GRIEF_GRAPHICS_IDS = ImmutableSet.of(
            SpotanimID.VFX_OCELLUS_QUADRANT_BOMB_IMPACT_01,
            SpotanimID.VFX_OCELLUS_QUADRANT_BOMB_DROP_01);

    private static final Map<Integer, HeadIcon> PRAYER_ATTACK_STYLES = ImmutableMap.of(
            SpotanimID.VFX_OCELLUS_PHASE3_MELEE_HYBRID, HeadIcon.MELEE,
            SpotanimID.VFX_OCELLUS_PHASE3_MELEE_HYBRID_MAGIC, HeadIcon.MAGIC,
            SpotanimID.VFX_OCELLUS_PHASE3_MELEE_HYBRID_RANGED, HeadIcon.RANGED);

    private static final Set<Integer> RIFT_EXPLOSION_ANIMATION_IDS = ImmutableSet.of(
            AnimationID.VFX_OCELLUS_VOIDFLARE_DEFAULT_DETONATE,
            AnimationID.VFX_OCELLUS_VOIDFLARE_HEAL_DETONATE,
            AnimationID.VFX_OCELLUS_VOIDFLARE_BUFF_DETONATE);

    // The parry hits 1 tick after it starts, then every 3 ticks from 5 ticks after, until it ends. Every raider gets a
    // hitsplat on each hit, so it only counts if they took damage.
    private static final int[] PARRY_HIT_OFFSETS = {1, 5, 8, 11};
    private static final int PRAYER_ATTACK_HIT_DELAY_IN_TICKS = 5;
    private static final int PURPLE_TILE_HIT_DELAY_IN_TICKS = 4;
    private static final int VOID_FLOOR_HIT_DELAY_IN_TICKS = 2;
    // Both skip the attack's own hit
    private static final int RANGE_ATTACK_VOID_EXPLOSION_DELAY_IN_TICKS = 5;
    private static final int MELEE_ATTACK_VOID_EXPLOSION_DELAY_IN_TICKS = 2;
    private static final int MIN_UNDER_SLASH_HIT = 20;
    private static final int MIN_VOID_EXPLOSION_HIT = 13;
    private static final int MAX_VOID_EXPLOSION_HIT = 17;

    private static final Set<Integer> NON_DAMAGE_HITSPLAT_IDS = ImmutableSet.of(HitsplatID.HEAL, HitsplatID.POISON,
            HitsplatID.VENOM, HitsplatID.DISEASE, HitsplatID.BURN);
    private static final int VOID_FLOOR_HIT_INTERVAL_IN_TICKS = 2;

    private final InstantHitTilesTracker aoeHitTiles = new InstantHitTilesTracker();
    private final InstantHitTilesTracker insolenceHitTiles = new InstantHitTilesTracker();
    private final InstantHitTilesTracker voidGriefHitTiles = new InstantHitTilesTracker();
    private final DelayedHitTilesTracker purpleHitTiles = new DelayedHitTilesTracker();
    // Void floor tile -> tick it spawned. It starts hurting 2 ticks after it spawns.
    private final Map<WorldPoint, Integer> voidFloorTiles = new HashMap<>();
    // Raider name -> tick they were last hurt by the void floor. It can't hurt the same raider on back-to-back ticks.
    private final Map<String, Integer> lastVoidFloorHitTicks = new HashMap<>();

    // His slashes also cut through him, along the rows and columns they cover
    private NPC ocellus;

    private final Set<Integer> parryHitTicks = new HashSet<>();
    // Raider name -> their biggest hit this tick
    private final Map<String, Integer> raiderHits = new HashMap<>();
    // Hit tick -> the prayer needed for it
    private final Map<Integer, HeadIcon> prayerAttackHits = new HashMap<>();
    // The style is a spot anim on Ocellus, which is only certain by the end of the tick he starts the attack
    private NPC prayerAttacker;

    private int untetherTick;
    private WorldArea untetherArea;

    // -1 when not between his range or melee attack and his prayer attack
    private int voidExplosionStartTick;
    private final Set<String> voidExplosionRaiders = new HashSet<>();

    private int riftExplosionCount;
    private final Set<String> fallingRaiders = new HashSet<>();

    public OcellusP3Detector() {
        untetherTick = -1;
        voidExplosionStartTick = -1;
    }

    @Override
    public void startup() {
        super.startup();
        for (NPC npc : client.getTopLevelWorldView().npcs()) {
            if (npc.getId() == NpcID.TFA_OCELLUS_P3) {
                ocellus = npc;
            }
        }

        // Void floor can already be down when the phase starts
        Tile[][] tiles = client.getTopLevelWorldView().getScene().getTiles()[client.getTopLevelWorldView().getPlane()];
        for (Tile[] column : tiles) {
            for (Tile tile : column) {
                GroundObject groundObject = tile == null ? null : tile.getGroundObject();
                if (groundObject != null && groundObject.getId() == ObjectID.TFA_OCELLUS_VOID_DEGEN_BOMB) {
                    voidFloorTiles.put(groundObject.getWorldLocation(), client.getTickCount());
                }
            }
        }
    }

    @Override
    public void cleanup() {
        aoeHitTiles.clear();
        insolenceHitTiles.clear();
        voidGriefHitTiles.clear();
        purpleHitTiles.clear();
        voidFloorTiles.clear();
        lastVoidFloorHitTicks.clear();
        ocellus = null;
        parryHitTicks.clear();
        raiderHits.clear();
        prayerAttackHits.clear();
        prayerAttacker = null;
        untetherTick = -1;
        untetherArea = null;
        voidExplosionStartTick = -1;
        voidExplosionRaiders.clear();
        riftExplosionCount = 0;
        fallingRaiders.clear();
    }

    @Override
    public RaidRoom getRaidRoom() {
        return OCELLUS;
    }

    // Every phase is the same Ocellus NPC changing form, so the phase is tracked from his id
    @Override
    public Integer getRequiredNpcId() {
        return NpcID.TFA_OCELLUS_P3;
    }

    @Override
    public List<TfaMistake> detectMistakes(@NonNull Raider raider) {
        List<TfaMistake> mistakes = new ArrayList<>();
        WorldPoint previousLocation = raider.getPreviousWorldLocation();
        int tick = client.getTickCount();

        if (voidGriefHitTiles.getActiveHitTiles().contains(previousLocation) || isVoidFloorHit(raider)) {
            mistakes.add(OCELLUS_P3_VOID_GRIEF);
        }

        if (isSlashHit(aoeHitTiles, raider)) {
            mistakes.add(OCELLUS_P3_AOE);
        }

        if (isSlashHit(insolenceHitTiles, raider)) {
            mistakes.add(OCELLUS_P3_INSOLENCE);
        }

        if (voidExplosionRaiders.contains(raider.getName())) {
            mistakes.add(OCELLUS_P3_VOID_EXPLOSION);
        }

        if (purpleHitTiles.getActiveHitTiles().contains(previousLocation)) {
            mistakes.add(OCELLUS_P2_PURPLE_TILE);
        }

        HeadIcon prayerAttackStyle = prayerAttackHits.get(tick);
        if (prayerAttackStyle != null && isOffPrayer(raider, prayerAttackStyle)) {
            mistakes.add(OCELLUS_P3_PRAYER_ATTACK);
        }

        if (parryHitTicks.contains(tick) && raiderHits.containsKey(raider.getName()) &&
                isOffPrayer(raider, HeadIcon.MELEE)) {
            mistakes.add(OCELLUS_P3_PARRY);
        }

        for (int i = 0; i < riftExplosionCount; i++) {
            mistakes.add(OCELLUS_P3_RIFT_EXPLOSION);
        }

        if (isUntetherHit(previousLocation)) {
            mistakes.add(OCELLUS_P3_UNTETHER);
        }

        if (fallingRaiders.contains(raider.getName())) {
            mistakes.add(OCELLUS_P3_FALL);
        }

        return mistakes;
    }

    @Override
    public void afterDetect() {
        int tick = client.getTickCount();
        parryHitTicks.remove(tick);
        prayerAttackHits.remove(tick);
        raiderHits.clear();
        voidExplosionRaiders.clear();
        riftExplosionCount = 0;
        fallingRaiders.clear();
    }

    // DEBUG ONLY -- remove before publishing
    @Override
    public void renderDebugTiles(DebugTilesOverlay overlay) {
        overlay.renderHitTiles(aoeHitTiles);
        overlay.renderHitTiles(insolenceHitTiles);
        overlay.renderHitTiles(voidGriefHitTiles);
        overlay.renderHitTiles(purpleHitTiles);
        overlay.renderLastingTiles(voidFloorTiles.keySet());
    }

    private boolean isOffPrayer(Raider raider, HeadIcon requiredHeadIcon) {
        if (vengeanceTracker.didPopVengeance(raider)) {
            return false;
        }

        return raider.getPlayer().getOverheadIcon() != requiredHeadIcon;
    }

    private boolean isSlashHit(InstantHitTilesTracker slashHitTiles, Raider raider) {
        WorldPoint location = raider.getPreviousWorldLocation();
        if (location == null) {
            return false;
        }

        Set<WorldPoint> hitTiles = slashHitTiles.getActiveHitTiles();
        if (hitTiles.contains(location)) {
            return true;
        }

        // Under him is worked out rather than shown, so only count it if it hurt
        if (ocellus == null || !ocellus.getWorldArea().contains(location) ||
                raiderHits.getOrDefault(raider.getName(), 0) < MIN_UNDER_SLASH_HIT) {
            return false;
        }
        return hitTiles.stream().anyMatch(tile -> tile.getX() == location.getX() || tile.getY() == location.getY());
    }

    private boolean isVoidFloorHit(Raider raider) {
        int tick = client.getTickCount();
        Integer spawnTick = voidFloorTiles.get(raider.getPreviousWorldLocation());
        if (spawnTick == null || tick - spawnTick < VOID_FLOOR_HIT_DELAY_IN_TICKS) {
            return false;
        }

        Integer lastHitTick = lastVoidFloorHitTicks.get(raider.getName());
        if (lastHitTick != null && tick - lastHitTick < VOID_FLOOR_HIT_INTERVAL_IN_TICKS) {
            return false;
        }

        lastVoidFloorHitTicks.put(raider.getName(), tick);
        return true;
    }

    private boolean isUntetherHit(WorldPoint location) {
        if (untetherArea == null || location == null) {
            return false;
        }

        // Everything outside him explodes first, then under him a tick later
        int tick = client.getTickCount();
        if (tick == untetherTick) {
            return !untetherArea.contains(location);
        } else if (tick == untetherTick + 1) {
            return untetherArea.contains(location);
        }
        return false;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        int tick = client.getTickCount();
        aoeHitTiles.onGameTick(tick);
        insolenceHitTiles.onGameTick(tick);
        voidGriefHitTiles.onGameTick(tick);
        purpleHitTiles.onGameTick(tick);

        if (prayerAttacker != null) {
            for (Map.Entry<Integer, HeadIcon> style : PRAYER_ATTACK_STYLES.entrySet()) {
                if (prayerAttacker.hasSpotAnim(style.getKey())) {
                    prayerAttackHits.put(tick + PRAYER_ATTACK_HIT_DELAY_IN_TICKS, style.getValue());
                }
            }
            prayerAttacker = null;
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        Actor actor = event.getActor();
        if (actor instanceof Player) {
            if (actor.getAnimation() == AnimationID.TFA_OCELLUS_VOID_PLAYER_DEATH) {
                fallingRaiders.add(actor.getName());
            }
            return;
        }

        if (!(actor instanceof NPC)) return;

        NPC npc = (NPC) actor;
        int tick = client.getTickCount();
        if (RIFT_EXPLOSION_ANIMATION_IDS.contains(npc.getAnimation())) {
            riftExplosionCount++;
        } else if (npc.getId() != NpcID.TFA_OCELLUS_P3) {
            return;
        }

        ocellus = npc;
        if (npc.getAnimation() == AnimationID.NPC_OCELLUS_PHASE3_DEATH) {
            shutdown();
        } else if (npc.getAnimation() == AnimationID.NPC_OCELLUS_PHASE3_PRAYER_IN) {
            for (int offset : PARRY_HIT_OFFSETS) {
                parryHitTicks.add(tick + offset);
            }
        } else if (npc.getAnimation() == AnimationID.NPC_OCELLUS_PHASE3_PRAYER_OUT) {
            parryHitTicks.removeIf(hitTick -> hitTick > tick);
        } else if (npc.getAnimation() == AnimationID.NPC_OCELLUS_PHASE3_DEFLECT_IN) { // His range attack
            voidExplosionStartTick = tick + RANGE_ATTACK_VOID_EXPLOSION_DELAY_IN_TICKS;
        } else if (npc.getAnimation() == AnimationID.NPC_OCELLUS_PHASE3_SOUL_FRAGMENT_START) { // His melee attack
            voidExplosionStartTick = tick + MELEE_ATTACK_VOID_EXPLOSION_DELAY_IN_TICKS;
        } else if (npc.getAnimation() == AnimationID.NPC_OCELLUS_PHASE3_MELEE_HYBRID) {
            prayerAttacker = npc;
            voidExplosionStartTick = -1;
        } else if (npc.getAnimation() == AnimationID.NPC_OCELLUS_PHASE3_UNLEASH_OUT) {
            untetherTick = tick;
            untetherArea = npc.getWorldArea();
        }
    }

    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated event) {
        GraphicsObject graphicsObject = event.getGraphicsObject();
        WorldPoint tile = getWorldPoint(graphicsObject);
        if (graphicsObject.getId() == SpotanimID.VFX_OCELLUS_P3_SHADOW_SLASH_SPOTANIM01) {
            aoeHitTiles.add(tile);
        } else if (graphicsObject.getId() == SpotanimID.VFX_OCELLUS_P3_SHADOW_SLASH_SUPERIOR_SPOTANIM01) {
            insolenceHitTiles.add(tile);
        } else if (VOID_GRIEF_GRAPHICS_IDS.contains(graphicsObject.getId())) {
            voidGriefHitTiles.add(tile);
        } else if (graphicsObject.getId() == SpotanimID.VFX_OCELLUS_P2_AOE_DODGE) {
            purpleHitTiles.put(client.getTickCount() + PURPLE_TILE_HIT_DELAY_IN_TICKS, tile);
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (!(event.getActor() instanceof Player)) return;
        if (NON_DAMAGE_HITSPLAT_IDS.contains(event.getHitsplat().getHitsplatType())) return;

        int amount = event.getHitsplat().getAmount();
        if (amount > 0) {
            raiderHits.merge(event.getActor().getName(), amount, Math::max);
        }

        boolean isVoidExplosionWindow = voidExplosionStartTick >= 0 && client.getTickCount() >= voidExplosionStartTick;
        if (isVoidExplosionWindow && amount >= MIN_VOID_EXPLOSION_HIT && amount <= MAX_VOID_EXPLOSION_HIT) {
            voidExplosionRaiders.add(event.getActor().getName());
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath event) {
        if (event.getActor() instanceof NPC && ((NPC) event.getActor()).getId() == NpcID.TFA_OCELLUS_P3) {
            shutdown();
        }
    }

    @Subscribe
    public void onGroundObjectSpawned(GroundObjectSpawned event) {
        if (event.getGroundObject().getId() == ObjectID.TFA_OCELLUS_VOID_DEGEN_BOMB) {
            voidFloorTiles.put(event.getGroundObject().getWorldLocation(), client.getTickCount());
        }
    }

    @Subscribe
    public void onGroundObjectDespawned(GroundObjectDespawned event) {
        if (event.getGroundObject().getId() == ObjectID.TFA_OCELLUS_VOID_DEGEN_BOMB) {
            voidFloorTiles.remove(event.getGroundObject().getWorldLocation());
        }
    }
}
