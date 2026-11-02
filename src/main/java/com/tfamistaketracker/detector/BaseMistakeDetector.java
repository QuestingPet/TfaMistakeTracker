package com.tfamistaketracker.detector;

import com.google.common.collect.ImmutableSet;
import com.tfamistaketracker.RaidRoom;
import com.tfamistaketracker.RaidState;
import com.tfamistaketracker.Raider;
import com.tfamistaketracker.TfaMistake;
import com.tfamistaketracker.detector.tracker.VengeanceTracker;
import com.tfamistaketracker.overlay.DebugTilesOverlay;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Projectile;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;

import javax.inject.Inject;
import java.util.List;
import java.util.Set;

/**
 * Interface for detecting mistakes during The Fractured Archives
 */
public abstract class BaseMistakeDetector {

    protected static final int CYCLES_PER_GAME_TICK = Constants.GAME_TICK_LENGTH / Constants.CLIENT_TICK_LENGTH;

    @Inject
    @Setter
    protected Client client;

    @Inject
    @Setter
    protected EventBus eventBus;

    @Inject
    @Setter
    protected RaidState raidState;

    @Inject
    @Setter
    protected VengeanceTracker vengeanceTracker;

    @Getter
    private boolean started;

    /**
     * Used to tell a detector to start listening for events.
     */
    public void startup() {
        if (started) return;

        started = true;
        cleanup();
        eventBus.register(this);
    }

    /**
     * Shutdown and cleanup state. This is always called when the plugin is shutdown, or when a detector is finished.
     */
    public void shutdown() {
        started = false;
        eventBus.unregister(this);
        cleanup();
    }

    /**
     * Cleanup all relevant state in the detector. This is called during startup to reset state, and shutdown to cleanup
     * This is also called for active detectors whenever raiders are loaded, which is during room transitions and room
     * resets (wipes).
     */
    public abstract void cleanup();

    /**
     * Retrieve the raid room that this detector should startup in. A null value means *all* rooms
     *
     * @return The raid room that the detector should startup in, or null for *all* rooms
     */
    public abstract RaidRoom getRaidRoom();

    /**
     * Detects mistakes for the given raider.
     * This is called during handling the {@link net.runelite.api.events.GameTick} event, each tick.
     *
     * @param raider - The raider to detect mistakes for
     * @return The list of {@link TfaMistake} detected on this tick
     */
    public abstract List<TfaMistake> detectMistakes(@NonNull Raider raider);

    /**
     * This method allows detectors to handle some logic after all detectMistakes methods have been invoked
     * for this {@link net.runelite.api.events.GameTick}. Commonly, this is to cleanup state from after this tick.
     */
    public abstract void afterDetect();

    /**
     * DEBUG ONLY -- remove before publishing. Draw whatever this detector is tracking on the debug tiles overlay.
     *
     * @param overlay The overlay to draw with
     */
    public void renderDebugTiles(DebugTilesOverlay overlay) {
    }

    /**
     * Determines whether or not this detector is currently detecting mistakes. Commonly this is by checking the current
     * {@link RaidRoom} in the {@link RaidState}
     *
     * @return True if the detector is detecting mistakes, else false
     */
    public boolean isDetectingMistakes() {
        if (getRaidRoom() == null) { // null means *all* rooms
            return raidState.isInRaid();
        }

        return raidState.getCurrentRoom() == getRaidRoom();
    }

    protected WorldPoint getWorldPoint(Actor actor) {
        return WorldPoint.fromLocal(client, actor.getLocalLocation());
    }

    protected WorldPoint getWorldPoint(GraphicsObject graphicsObject) {
        return WorldPoint.fromLocal(client, graphicsObject.getLocation());
    }

    /**
     * Computes the WorldPoints in the given area, expanded outward by the given number of tiles in every direction.
     * For example, a 1x1 area with an expansion of 1 is the 3x3 area centred on it.
     *
     * @param area             The area, e.g. an NPC's footprint
     * @param tilesToExpandBy The number of tiles to expand the area outward by
     * @return The set of WorldPoints in the expanded area
     */
    protected Set<WorldPoint> computeTilesAround(WorldArea area, int tilesToExpandBy) {
        return ImmutableSet.copyOf(new WorldArea(area.getX() - tilesToExpandBy, area.getY() - tilesToExpandBy,
                area.getWidth() + 2 * tilesToExpandBy, area.getHeight() + 2 * tilesToExpandBy,
                area.getPlane()).toWorldPointList());
    }

    /**
     * Computes the WorldPoints within the given number of tiles of the center, e.g. 1 tile is the 3x3 centred on it.
     *
     * @param center          The center tile
     * @param tilesToExpandBy The number of tiles to expand outward from the center by
     * @return The set of WorldPoints around and including the center
     */
    protected Set<WorldPoint> computeTilesAround(WorldPoint center, int tilesToExpandBy) {
        return computeTilesAround(new WorldArea(center, 1, 1), tilesToExpandBy);
    }

    /**
     * Calculates and retrieves the activation tick for the specified GraphicsObject based on the start cycle and
     * the given hit delay.
     *
     * @param graphicsObject The graphics object
     * @param hitDelay       The delay in ticks from when the graphics object animation starts and when it causes a hit
     * @return The activation tick for when the graphics object will denote a hit on the player
     */
    protected int getActivationTick(GraphicsObject graphicsObject, int hitDelay) {
        int ticksToStart = (graphicsObject.getStartCycle() - client.getGameCycle()) / CYCLES_PER_GAME_TICK;
        return client.getTickCount() + ticksToStart + hitDelay; // Add the hit delay for how long between start to hit
    }

    /**
     * Calculates and retrieves the activation tick for the specified Projectile based on the remaining cycles.
     *
     * @param projectile The projectile object
     * @return The activation tick for when the projectile object will reach its target
     */
    protected int getActivationTick(Projectile projectile) {
        int ticksRemaining = projectile.getRemainingCycles() / CYCLES_PER_GAME_TICK;
        return client.getTickCount() + ticksRemaining;
    }
}
