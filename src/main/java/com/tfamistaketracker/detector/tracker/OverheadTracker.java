package com.tfamistaketracker.detector.tracker;

import com.tfamistaketracker.Raider;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.HeadIcon;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.events.ProjectileMoved;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.tfamistaketracker.TfaMistakeTrackerPlugin.CYCLES_PER_GAME_TICK;

/**
 * Tracks projectiles that require a specific overhead prayer. A projectile activates on the tick it hits, and the
 * target's prayer is taken from the moment specified by its {@link Attack}. Overheads are always read on a GameTick.
 */
@RequiredArgsConstructor
@Slf4j
public class OverheadTracker extends DelayedObjectsTracker<Projectile> {

    /**
     * When the prayer for an attack must be correct.
     */
    public enum PrayerCheck {
        // On the tick the source started its attack animation, see snapshotOverheads
        ANIMATION,
        // On the tick the projectile is first seen, plus the offset
        SPAWN,
        // On the tick the projectile hits, plus the offset
        HIT
    }

    @Value(staticConstructor = "of")
    public static class Attack {
        HeadIcon requiredHeadIcon;
        PrayerCheck prayerCheck;
        int tickOffset;

        public static Attack of(HeadIcon requiredHeadIcon, PrayerCheck prayerCheck) {
            return of(requiredHeadIcon, prayerCheck, 0);
        }
    }

    // Projectile ID -> attack
    private final Map<Integer, Attack> attacks;

    private final Set<Projectile> trackedProjectiles = new HashSet<>();

    // Source actor -> raider name -> overhead on the tick the source started its attack animation
    private final Map<Actor, Map<String, HeadIcon>> animationSnapshots = new HashMap<>();

    // Projectile -> tick to read its target's overhead on, for SPAWN and HIT
    private final Map<Projectile, Integer> prayerCheckTicks = new HashMap<>();

    // Projectile -> its target's overhead at the deciding moment
    private final Map<Projectile, HeadIcon> decidingOverheads = new HashMap<>();

    // Projectile -> tick it hits on
    private final Map<Projectile, Integer> hitTicks = new HashMap<>();

    /**
     * Snapshots every raider's current overhead for attacks from the given source. Call this from a GameTick on the
     * tick the source started its attack animation, for {@link PrayerCheck#ANIMATION} attacks.
     *
     * @param source  The actor doing the attack
     * @param raiders All raiders
     */
    public void snapshotOverheads(@NonNull Actor source, @NonNull Collection<Raider> raiders) {
        Map<String, HeadIcon> snapshot = new HashMap<>();
        for (Raider raider : raiders) {
            snapshot.put(raider.getName(), raider.getPlayer().getOverheadIcon());
        }
        animationSnapshots.put(source, snapshot);
    }

    /**
     * Tracks a Projectile from the specified ProjectileMoved event
     *
     * @param event       The event containing the Projectile and metadata
     * @param currentTick The current game tick
     */
    public void trackProjectile(@NonNull ProjectileMoved event, int currentTick) {
        Projectile projectile = event.getProjectile();
        Attack attack = attacks.get(projectile.getId());
        if (attack == null || trackedProjectiles.contains(projectile)) {
            return;
        }

        if (!hasEnoughRemainingCycles(projectile)) {
            // There's no way there's a new projectile that only has at most 1 game tick left. It's probably
            // hanging around from a previous attack, so let's ignore
            return;
        }

        int hitTick = currentTick + projectile.getRemainingCycles() / CYCLES_PER_GAME_TICK;
        put(hitTick, projectile);
        trackedProjectiles.add(projectile);
        hitTicks.put(projectile, hitTick);

        switch (attack.getPrayerCheck()) {
            case ANIMATION:
                Map<String, HeadIcon> snapshot = animationSnapshots.get(projectile.getSourceActor());
                Actor target = projectile.getTargetActor();
                if (snapshot != null && target != null && snapshot.containsKey(target.getName())) {
                    decidingOverheads.put(projectile, snapshot.get(target.getName()));
                }
                break;
            case SPAWN:
                prayerCheckTicks.put(projectile, currentTick + attack.getTickOffset());
                break;
            case HIT:
                prayerCheckTicks.put(projectile, hitTick + attack.getTickOffset());
                break;
        }
    }

    @Override
    public void onGameTick(@NonNull Integer gameTick) {
        super.onGameTick(gameTick);

        prayerCheckTicks.entrySet().removeIf(entry -> {
            if (entry.getValue() != gameTick.intValue()) return false;

            Actor target = entry.getKey().getTargetActor();
            if (target instanceof Player) {
                decidingOverheads.put(entry.getKey(), ((Player) target).getOverheadIcon());
            }
            return true;
        });

        hitTicks.entrySet().removeIf(entry -> {
            boolean hit = entry.getValue() < gameTick;
            if (hit) {
                prayerCheckTicks.remove(entry.getKey());
                decidingOverheads.remove(entry.getKey());
            }
            return hit;
        });

        // Clear state of projectiles that have at most 1 game tick left, as they can't be added anyway
        trackedProjectiles.removeIf(projectile -> !hasEnoughRemainingCycles(projectile));
    }

    @Override
    public void clear() {
        super.clear();
        trackedProjectiles.clear();
        animationSnapshots.clear();
        prayerCheckTicks.clear();
        decidingOverheads.clear();
        hitTicks.clear();
    }

    public Set<Projectile> getActiveProjectiles() {
        return getActiveObjects();
    }

    /**
     * Determine whether the given raider missed their prayer for the projectile hitting them this tick
     *
     * @param raider The raider
     * @return True if the raider missed their prayer, false otherwise
     */
    public boolean didMissPrayer(Raider raider) {
        Projectile activeProjectile = getActiveProjectileForRaider(raider);
        if (activeProjectile == null) {
            return false;
        }

        Attack attack = attacks.get(activeProjectile.getId());
        if (attack == null) {
            return false;
        }

        HeadIcon overhead = decidingOverheads.containsKey(activeProjectile)
                ? decidingOverheads.get(activeProjectile)
                : raider.getPlayer().getOverheadIcon();
        return overhead != attack.getRequiredHeadIcon();
    }

    /**
     * Return the current active projectile for the specified Raider
     *
     * @param raider The raider
     * @return The projectile that activated on this tick for the raider
     */
    public Projectile getActiveProjectileForRaider(Raider raider) {
        for (Projectile projectile : getActiveProjectiles()) {
            if (projectile != null &&
                    raider.getPlayer().equals(projectile.getTargetActor())) {
                return projectile;
            }
        }

        return null;
    }

    // DEBUG ONLY -- remove before publishing
    public Map<Projectile, Integer> getHitTicks() {
        return Collections.unmodifiableMap(hitTicks);
    }

    // DEBUG ONLY -- remove before publishing
    public Attack getAttack(Projectile projectile) {
        return attacks.get(projectile.getId());
    }

    // DEBUG ONLY -- remove before publishing
    public HeadIcon getDecidingOverhead(Projectile projectile) {
        return decidingOverheads.get(projectile);
    }

    private boolean hasEnoughRemainingCycles(Projectile projectile) {
        // There's no way there's a new projectile that only has at most 1 game tick left. It's probably
        // hanging around from a previous attack, so let's ignore
        return projectile.getRemainingCycles() > CYCLES_PER_GAME_TICK;
    }
}
