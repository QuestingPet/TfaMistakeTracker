package com.tfamistaketracker.overlay;

import com.tfamistaketracker.RaidState;
import com.tfamistaketracker.Raider;
import com.tfamistaketracker.TfaMistakeTrackerConfig;
import com.tfamistaketracker.detector.BaseMistakeDetector;
import com.tfamistaketracker.detector.MistakeDetectorManager;
import com.tfamistaketracker.detector.tracker.DelayedHitTilesTracker;
import com.tfamistaketracker.detector.tracker.InstantHitTilesTracker;
import com.tfamistaketracker.detector.tracker.OverheadTracker;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.HeadIcon;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Projectile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import javax.inject.Named;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.Collection;
import java.util.Map;

/**
 * DEBUG ONLY -- remove before publishing
 * <p>
 * Draws the tiles and projectiles the started detectors are tracking. Active hit tiles are green, pending hit tiles
 * are red with the ticks until they activate, lasting hazard tiles are orange, and each raider's previous tile is
 * magenta. Tracked projectiles show the required prayer and the ticks until they hit above their target.
 */
public class DebugTilesOverlay extends Overlay {

    private static final Color ACTIVE_COLOR = Color.GREEN;
    private static final Color PENDING_COLOR = Color.RED;
    private static final Color LASTING_COLOR = Color.ORANGE;
    private static final Color SAFE_COLOR = Color.WHITE;
    private static final Color RAIDER_COLOR = Color.MAGENTA;
    private static final Color PROJECTILE_COLOR = Color.CYAN;

    private final Client client;
    private final RaidState raidState;
    private final TfaMistakeTrackerConfig config;
    private final MistakeDetectorManager mistakeDetectorManager;
    private final boolean developerMode;

    private Graphics2D graphics;

    @Inject
    public DebugTilesOverlay(Client client,
                             RaidState raidState,
                             TfaMistakeTrackerConfig config,
                             MistakeDetectorManager mistakeDetectorManager,
                             @Named("developerMode") boolean developerMode) {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(PRIORITY_MED);

        this.client = client;
        this.raidState = raidState;
        this.config = config;
        this.mistakeDetectorManager = mistakeDetectorManager;
        this.developerMode = developerMode;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!developerMode) return null;
        if (!config.debugTiles()) return null;

        this.graphics = graphics;

        for (BaseMistakeDetector detector : mistakeDetectorManager.getMistakeDetectors()) {
            if (detector.isStarted()) {
                detector.renderDebugTiles(this);
            }
        }

        for (Raider raider : raidState.getRaiders().values()) {
            if (raider.getPreviousWorldLocationForOverlay() != null) {
                renderTile(raider.getPreviousWorldLocationForOverlay(), RAIDER_COLOR, null);
            }
        }

        this.graphics = null;
        return null;
    }

    public void renderHitTiles(InstantHitTilesTracker tracker) {
        tracker.getActiveHitTiles().forEach(tile -> renderTile(tile, ACTIVE_COLOR, null));
    }

    public void renderHitTiles(DelayedHitTilesTracker tracker) {
        int tick = client.getTickCount();
        tracker.getDelayedObjects().forEach((activationTick, tiles) -> tiles.forEach(tile ->
                renderTile(tile, PENDING_COLOR, String.valueOf(activationTick - tick))));
        tracker.getActiveHitTiles().forEach(tile -> renderTile(tile, ACTIVE_COLOR, null));
    }

    public void renderLastingTiles(Collection<WorldPoint> tiles) {
        tiles.forEach(tile -> renderTile(tile, LASTING_COLOR, null));
    }

    public void renderSafeTiles(Collection<WorldPoint> tiles) {
        tiles.forEach(tile -> renderTile(tile, SAFE_COLOR, "safe"));
    }

    public void renderActiveTiles(Collection<WorldPoint> tiles) {
        tiles.forEach(tile -> renderTile(tile, ACTIVE_COLOR, null));
    }

    public void renderProjectiles(OverheadTracker tracker) {
        int tick = client.getTickCount();
        for (Map.Entry<Projectile, Integer> entry : tracker.getHitTicks().entrySet()) {
            Actor target = entry.getKey().getTargetActor();
            OverheadTracker.Attack attack = tracker.getAttack(entry.getKey());
            if (target == null || attack == null) continue;

            String text = attack.getRequiredHeadIcon() + " " + (entry.getValue() - tick);
            HeadIcon decidingOverhead = tracker.getDecidingOverhead(entry.getKey());
            if (decidingOverhead != null) {
                text += " (had " + decidingOverhead + ")";
            }

            Point textPoint = target.getCanvasTextLocation(graphics, text, target.getLogicalHeight() + 60);
            if (textPoint != null) {
                OverlayUtil.renderTextLocation(graphics, textPoint, text, PROJECTILE_COLOR);
            }
        }
    }

    private void renderTile(WorldPoint worldPoint, Color color, String text) {
        LocalPoint dest = LocalPoint.fromWorld(client, worldPoint);
        if (dest == null) {
            return;
        }

        Polygon poly = Perspective.getCanvasTilePoly(client, dest);
        if (poly == null) {
            return;
        }

        OverlayUtil.renderPolygon(graphics, poly, color);

        if (text == null) {
            return;
        }

        Point textPoint = Perspective.getCanvasTextLocation(client, graphics, dest, text, 0);
        if (textPoint != null) {
            OverlayUtil.renderTextLocation(graphics, textPoint, text, color);
        }
    }
}
