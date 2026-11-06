package com.tfamistaketracker.detector.boss;

import com.tfamistaketracker.RaidRoom;
import com.tfamistaketracker.Raider;
import com.tfamistaketracker.TfaMistake;
import com.tfamistaketracker.detector.BaseMistakeDetector;
import com.tfamistaketracker.detector.tracker.DelayedHitTilesTracker;
import com.tfamistaketracker.detector.tracker.InstantHitTilesTracker;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.GraphicsObject;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.NpcDespawned;
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

import static com.tfamistaketracker.RaidRoom.XARTH;
import static com.tfamistaketracker.TfaMistake.XARTH_CORPSE_HEAL;
import static com.tfamistaketracker.TfaMistake.XARTH_EXPLOSION;
import static com.tfamistaketracker.TfaMistake.XARTH_FIRE;
import static com.tfamistaketracker.TfaMistake.XARTH_MELEE;
import static com.tfamistaketracker.TfaMistake.XARTH_TETHER;

/**
 * Xarth's tether marks each raider's tile and hits them a few ticks later if they're still on it. His flame blast
 * explodes most of the arena except behind cover, both after a tether and when corpses reach him and heal him. Fire
 * left on the floor burns anyone standing on it, and later in the fight he slashes whoever he's attacking.
 */
@Slf4j
@Singleton
public class XarthDetector extends BaseMistakeDetector {

    // Fire still burns while he's dying, so mistakes only stop once he's gone
    private boolean isXarthDead;
    private int corpseHealCount;

    private final DelayedHitTilesTracker tetherHitTiles = new DelayedHitTilesTracker();
    private final InstantHitTilesTracker explosionHitTiles = new InstantHitTilesTracker();
    // Fire tile -> tick it spawned. Fire doesn't burn on the tick it spawns.
    private final Map<WorldPoint, Integer> fireTiles = new HashMap<>();
    private final Set<String> meleeTargets = new HashSet<>();

    public XarthDetector() {
        isXarthDead = false;
        corpseHealCount = 0;
    }

    @Override
    public void cleanup() {
        isXarthDead = false;
        clearTrackers();
        fireTiles.clear();
    }

    private void clearTrackers() {
        corpseHealCount = 0;
        tetherHitTiles.clear();
        explosionHitTiles.clear();
        meleeTargets.clear();
    }

    @Override
    public RaidRoom getRaidRoom() {
        return XARTH;
    }

    @Override
    public List<TfaMistake> detectMistakes(@NonNull Raider raider) {
        List<TfaMistake> mistakes = new ArrayList<>();
        if (isXarthDead) {
            return mistakes;
        }

        WorldPoint previousLocation = raider.getPreviousWorldLocation();

        if (isOnFire(previousLocation)) {
            mistakes.add(XARTH_FIRE);
        }

        if (isMeleeHit(raider)) {
            mistakes.add(XARTH_MELEE);
        }

        if (tetherHitTiles.getActiveHitTiles().contains(previousLocation)) {
            mistakes.add(XARTH_TETHER);
        }

        if (explosionHitTiles.getActiveHitTiles().contains(previousLocation)) {
            mistakes.add(XARTH_EXPLOSION);
        }

        for (int i = 0; i < corpseHealCount; i++) {
            mistakes.add(XARTH_CORPSE_HEAL);
        }

        return mistakes;
    }

    @Override
    public void afterDetect() {
        meleeTargets.clear();
        corpseHealCount = 0;
    }

    private boolean isOnFire(WorldPoint location) {
        Integer spawnTick = fireTiles.get(location);
        return spawnTick != null && spawnTick != client.getTickCount();
    }

    private boolean isMeleeHit(Raider raider) {
        if (!meleeTargets.contains(raider.getName())) {
            return false;
        }

        if (vengeanceTracker.didPopVengeance(raider)) {
            return false;
        }

        return raider.getPlayer().getOverheadIcon() != HeadIcon.MELEE;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        tetherHitTiles.onGameTick(client.getTickCount());
        explosionHitTiles.onGameTick(client.getTickCount());
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        Actor actor = event.getActor();
        if (!(actor instanceof NPC) || isXarthDead) return;

        NPC npc = (NPC) actor;
        if (npc.getId() == NpcID.TFA_XARTH) {
            if (npc.getAnimation() == AnimationID.XARTH_ATTACK_MELEE_01 &&
                    npc.getInteracting() instanceof Player) {
                meleeTargets.add(npc.getInteracting().getName());
            }
        } else if (npc.getId() == NpcID.TFA_XARTH_CORPSE && npc.getAnimation() == AnimationID.XARTH_CORPSE_ABSORBED_01) {
            corpseHealCount++;
        }
    }

    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated event) {
        if (isXarthDead) return;

        GraphicsObject graphicsObject = event.getGraphicsObject();
        if (graphicsObject.getId() == SpotanimID.VFX_XARTH_AOE_HITANIM_01) {
            // The hit lands when the graphic starts
            tetherHitTiles.put(getActivationTick(graphicsObject, 0), getWorldPoint(graphicsObject));
        } else if (graphicsObject.getId() == SpotanimID.VFX_VMQ4_ENNIUS_FIRE_EXPLOSION) {
            explosionHitTiles.add(getWorldPoint(graphicsObject));
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        if (event.getNpc().getId() == NpcID.TFA_XARTH) {
            isXarthDead = true;
            clearTrackers();
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        if (event.getGameObject().getId() == ObjectID.TFA_XARTH_ARENAFIRE) {
            fireTiles.put(event.getGameObject().getWorldLocation(), client.getTickCount());
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        if (event.getGameObject().getId() == ObjectID.TFA_XARTH_ARENAFIRE) {
            fireTiles.remove(event.getGameObject().getWorldLocation());
        }
    }
}
