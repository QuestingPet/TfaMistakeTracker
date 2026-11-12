package com.tfamistaketracker.detector.boss;

import com.google.common.collect.ImmutableMap;
import com.tfamistaketracker.RaidRoom;
import com.tfamistaketracker.Raider;
import com.tfamistaketracker.TfaMistake;
import com.tfamistaketracker.detector.BaseMistakeDetector;
import com.tfamistaketracker.detector.tracker.AppliedHitsplatsTracker;
import com.tfamistaketracker.detector.tracker.OverheadTracker;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.HeadIcon;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tfamistaketracker.RaidRoom.MOIA;
import static com.tfamistaketracker.TfaMistake.MOIA_BINDER_MELEE;
import static com.tfamistaketracker.TfaMistake.MOIA_MAGIC;
import static com.tfamistaketracker.TfaMistake.MOIA_RANGE;

/**
 * Moia's ranged dagger hits the tick after her attack animation, but the prayer that counts is the one up on the
 * animation tick. Her magic dagger comes right after she teleports and needs magic prayer when it lands. Undead
 * binders melee everyone within 2 tiles on the tick they animate.
 */
@Slf4j
@Singleton
public class MoiaDetector extends BaseMistakeDetector {

    private static final Map<Integer, OverheadTracker.Attack> MAGIC_ATTACKS = ImmutableMap.of(
            SpotanimID.MOIA_DAGGER_MAGIC,
            OverheadTracker.Attack.of(HeadIcon.MAGIC, OverheadTracker.PrayerCheck.HIT));

    private static final int BINDER_MELEE_RANGE = 2;

    // Not gated on Moia being spawned, since she despawns during the enrage while the binders keep attacking
    private boolean isMoiaDead;
    private boolean didMoiaAttack;

    // Raider name -> overhead on Moia's last attack animation tick
    private final Map<String, HeadIcon> attackOverheads = new HashMap<>();
    private final Set<Projectile> rangedProjectiles = new HashSet<>();
    private final Set<String> rangedTargets = new HashSet<>();
    private final List<WorldPoint> attackingBinders = new ArrayList<>();
    private final OverheadTracker magicOverheadTracker = new OverheadTracker(MAGIC_ATTACKS);
    private final AppliedHitsplatsTracker appliedHitsplats = new AppliedHitsplatsTracker();

    public MoiaDetector() {
        isMoiaDead = false;
        didMoiaAttack = false;
    }

    @Override
    public void cleanup() {
        isMoiaDead = false;
        didMoiaAttack = false;
        attackOverheads.clear();
        rangedProjectiles.clear();
        rangedTargets.clear();
        attackingBinders.clear();
        magicOverheadTracker.clear();
        appliedHitsplats.clear();
    }

    @Override
    public RaidRoom getRaidRoom() {
        return MOIA;
    }

    @Override
    public List<TfaMistake> detectMistakes(@NonNull Raider raider) {
        List<TfaMistake> mistakes = new ArrayList<>();
        if (isMoiaDead) {
            return mistakes;
        }

        if (isBinderMeleeHit(raider)) {
            mistakes.add(MOIA_BINDER_MELEE);
        }

        if (isRangedHit(raider)) {
            mistakes.add(MOIA_RANGE);
        }

        if (magicOverheadTracker.didMissPrayer(raider) && appliedHitsplats.popHitsplatApplied(raider.getName())) {
            mistakes.add(MOIA_MAGIC);
        }

        return mistakes;
    }

    @Override
    public void afterDetect() {
        rangedTargets.clear();
        attackingBinders.clear();
        appliedHitsplats.clear();
    }

    private boolean isBinderMeleeHit(Raider raider) {
        if (attackingBinders.isEmpty() || raider.getPlayer().getOverheadIcon() == HeadIcon.MELEE) {
            return false;
        }

        boolean inRange = attackingBinders.stream().anyMatch(binder ->
                isInMeleeRange(binder, raider.getPreviousWorldLocation()));
        return inRange && appliedHitsplats.popHitsplatApplied(raider.getName());
    }

    private boolean isInMeleeRange(WorldPoint binder, WorldPoint location) {
        return location != null && binder.getPlane() == location.getPlane() &&
                Math.max(Math.abs(binder.getX() - location.getX()), Math.abs(binder.getY() - location.getY())) <=
                        BINDER_MELEE_RANGE;
    }

    private boolean isRangedHit(Raider raider) {
        if (!rangedTargets.contains(raider.getName())) {
            return false;
        }

        HeadIcon overhead = attackOverheads.get(raider.getName());
        return overhead != HeadIcon.RANGED && appliedHitsplats.popHitsplatApplied(raider.getName());
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        magicOverheadTracker.onGameTick(client.getTickCount());

        if (didMoiaAttack) {
            didMoiaAttack = false;
            attackOverheads.clear();
            rangedProjectiles.clear();
            for (Player player : client.getTopLevelWorldView().players()) {
                if (player != null && raidState.isRaider(player)) {
                    attackOverheads.put(player.getName(), player.getOverheadIcon());
                }
            }
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (!(event.getActor() instanceof NPC)) return;

        NPC npc = (NPC) event.getActor();
        if (npc.getId() == NpcID.TFA_MOIA) {
            if (npc.getAnimation() == AnimationID.MOIA_PROJECTILE_ATTACK01) {
                didMoiaAttack = true;
            } else if (npc.getAnimation() == AnimationID.MOIA_DEATH01) {
                isMoiaDead = true;
            }
        } else if (npc.getId() == NpcID.TFA_MOIA_MINION && npc.getAnimation() == AnimationID.MOIA_MINION_ATTACK) {
            attackingBinders.add(npc.getWorldLocation());
        }
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        Projectile projectile = event.getProjectile();
        if (projectile.getId() == SpotanimID.MOIA_DAGGER_MAGIC) {
            magicOverheadTracker.trackProjectile(event, client.getTickCount());
        } else if (projectile.getId() == SpotanimID.MOIA_DAGGER && projectile.getTargetActor() instanceof Player &&
                rangedProjectiles.add(projectile)) {
            rangedTargets.add(projectile.getTargetActor().getName());
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (!(event.getActor() instanceof Player)) return;

        int type = event.getHitsplat().getHitsplatType();
        if (type == HitsplatID.HEAL || type == HitsplatID.POISON || type == HitsplatID.VENOM ||
                type == HitsplatID.DISEASE || type == HitsplatID.BURN) return;

        appliedHitsplats.addHitsplatForRaider(event.getActor().getName());
    }
}
