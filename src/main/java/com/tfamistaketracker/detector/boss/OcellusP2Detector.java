package com.tfamistaketracker.detector.boss;

import com.google.common.collect.ImmutableSet;
import com.tfamistaketracker.RaidRoom;
import com.tfamistaketracker.Raider;
import com.tfamistaketracker.TfaMistake;
import com.tfamistaketracker.detector.BaseMistakeDetector;
import com.tfamistaketracker.detector.tracker.DelayedHitTilesTracker;
import com.tfamistaketracker.overlay.DebugTilesOverlay;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.HitsplatID;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
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
import java.util.Set;

import static com.tfamistaketracker.RaidRoom.OCELLUS;
import static com.tfamistaketracker.TfaMistake.OCELLUS_P2_PORTAL;
import static com.tfamistaketracker.TfaMistake.OCELLUS_P2_PURPLE_TILE;

/**
 * In phase 2, lines of portals fire projectiles across each quadrant, hitting anyone who isn't on that call's safe
 * tiles as they pass. The safe tiles rotate, so instead of tracking them, a portal hit is any big hit shortly after
 * the portals fire. Purple tiles explode a few ticks after they appear.
 */
@Slf4j
@Singleton
public class OcellusP2Detector extends BaseMistakeDetector {

    private static final int MIN_PORTAL_HIT = 15;
    private static final int PORTAL_HIT_WINDOW_IN_TICKS = 2;
    private static final int PURPLE_TILE_HIT_DELAY_IN_TICKS = 4;

    private static final Set<Integer> NON_DAMAGE_HITSPLAT_IDS = ImmutableSet.of(HitsplatID.HEAL, HitsplatID.POISON,
            HitsplatID.VENOM, HitsplatID.DISEASE, HitsplatID.BURN);

    private int lastPortalSpawnTick;

    private final Set<Projectile> trackedPortalProjectiles = new HashSet<>();
    private final Set<String> bigHitRaiders = new HashSet<>();
    private final DelayedHitTilesTracker purpleHitTiles = new DelayedHitTilesTracker();

    public OcellusP2Detector() {
        lastPortalSpawnTick = -1;
    }

    @Override
    public void cleanup() {
        lastPortalSpawnTick = -1;
        trackedPortalProjectiles.clear();
        bigHitRaiders.clear();
        purpleHitTiles.clear();
    }

    @Override
    public RaidRoom getRaidRoom() {
        return OCELLUS;
    }

    // Every phase is the same Ocellus NPC changing form, so the phase is tracked from his id
    @Override
    public Integer getRequiredNpcId() {
        return NpcID.TFA_OCELLUS_P2;
    }

    @Override
    public List<TfaMistake> detectMistakes(@NonNull Raider raider) {
        List<TfaMistake> mistakes = new ArrayList<>();
        if (purpleHitTiles.getActiveHitTiles().contains(raider.getPreviousWorldLocation())) {
            mistakes.add(OCELLUS_P2_PURPLE_TILE);
        } else if (isPortalHit(raider)) {
            mistakes.add(OCELLUS_P2_PORTAL);
        }

        return mistakes;
    }

    @Override
    public void afterDetect() {
        bigHitRaiders.clear();
    }

    // DEBUG ONLY -- remove before publishing
    @Override
    public void renderDebugTiles(DebugTilesOverlay overlay) {
        overlay.renderHitTiles(purpleHitTiles);
    }

    private boolean isPortalHit(Raider raider) {
        return bigHitRaiders.contains(raider.getName()) && lastPortalSpawnTick >= 0 &&
                client.getTickCount() - lastPortalSpawnTick <= PORTAL_HIT_WINDOW_IN_TICKS;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        purpleHitTiles.onGameTick(client.getTickCount());
        trackedPortalProjectiles.removeIf(projectile -> projectile.getRemainingCycles() <= 0);
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        if (event.getProjectile().getId() != SpotanimID.VFX_OCELLUS_PORTAL_PROJECTILE) return;

        if (trackedPortalProjectiles.add(event.getProjectile())) {
            lastPortalSpawnTick = client.getTickCount();
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (!(event.getActor() instanceof Player)) return;
        if (NON_DAMAGE_HITSPLAT_IDS.contains(event.getHitsplat().getHitsplatType())) return;

        if (event.getHitsplat().getAmount() >= MIN_PORTAL_HIT) {
            bigHitRaiders.add(event.getActor().getName());
        }
    }

    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated event) {
        if (event.getGraphicsObject().getId() != SpotanimID.VFX_OCELLUS_P2_AOE_DODGE) return;

        purpleHitTiles.put(client.getTickCount() + PURPLE_TILE_HIT_DELAY_IN_TICKS,
                getWorldPoint(event.getGraphicsObject()));
    }
}
