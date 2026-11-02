package com.tfamistaketracker.debug;

import com.tfamistaketracker.RaidRoom;
import com.tfamistaketracker.RaidState;
import com.tfamistaketracker.TfaMistakeTrackerConfig;
import com.tfamistaketracker.TfaMistakeTrackerPlugin;
import com.tfamistaketracker.events.InRaidChanged;
import com.tfamistaketracker.events.RaidEntered;
import com.tfamistaketracker.events.RaidRoomChanged;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Client;
import net.runelite.api.GraphicsObject;
import net.runelite.api.HeadIcon;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.AreaSoundEffectPlayed;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.SoundEffectPlayed;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DEBUG ONLY -- remove before publishing.
 * <p>
 * Logs game events to debug-logs/ in the working directory. Each line is tab-separated: tick, event, key=value pairs.
 */
@Slf4j
@Singleton
public class EventLogger {

    private static final String LOG_DIR_NAME = "debug-logs";
    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Client client;
    private final EventBus eventBus;
    private final KeyManager keyManager;
    private final TfaMistakeTrackerConfig config;
    private final RaidState raidState;
    private final boolean developerMode;

    // Projectiles we've already logged, so each one is only logged once. Weak so finished projectiles are dropped.
    private final Set<Projectile> loggedProjectiles = Collections.newSetFromMap(new WeakHashMap<>());

    private final AtomicInteger markerCount = new AtomicInteger();

    // Last seen overhead prayer of each player, by name. Only accessed on the client thread.
    private final Map<String, HeadIcon> overheadPrayers = new HashMap<>();

    // Last logged tile of each player (by name) and NPC (by index). Only accessed on the client thread.
    private final Map<String, WorldPoint> lastTiles = new HashMap<>();

    private volatile LogFile logFile;

    private final HotkeyListener markerListener;

    @Inject
    public EventLogger(Client client, EventBus eventBus, KeyManager keyManager, TfaMistakeTrackerConfig config,
                       RaidState raidState, @Named("developerMode") boolean developerMode) {
        this.client = client;
        this.eventBus = eventBus;
        this.keyManager = keyManager;
        this.config = config;
        this.raidState = raidState;
        this.developerMode = developerMode;

        this.markerListener = new HotkeyListener(config::eventLoggerMarkerKey) {
            @Override
            public void hotkeyPressed() {
                if (isLogging()) {
                    write("MARK", "#" + markerCount.incrementAndGet());
                }
            }
        };
    }

    public void startUp() {
        eventBus.register(this);
        keyManager.registerKeyListener(markerListener);
        if (shouldLog()) {
            open();
        }
    }

    public void shutDown() {
        keyManager.unregisterKeyListener(markerListener);
        eventBus.unregister(this);
        close();
    }

    public boolean isLogging() {
        return logFile != null;
    }

    public int getLastMarker() {
        return markerCount.get();
    }

    /**
     * @return The absolute path of the current log file, or null if not logging
     */
    public String getLogFilePath() {
        LogFile f = logFile;
        return f == null ? null : f.getAbsolutePath();
    }

    private boolean shouldLog() {
        return developerMode && config.eventLogger();
    }

    private void open() {
        if (isLogging()) return;

        Filepath dir = Filepath.Unchecked.getRooted(Paths.get(System.getProperty("user.dir"))).joinSegment(LOG_DIR_NAME);
        String fileName = "tfa-events-" + LocalDateTime.now().format(FILE_TIME_FORMAT) + ".log";
        LogFile f = new LogFile(dir, dir.joinSegment(fileName));
        loggedProjectiles.clear();
        overheadPrayers.clear();
        lastTiles.clear();
        markerCount.set(0);
        logFile = f;
        log.debug("Event logger writing to {}", f.getAbsolutePath());
    }

    private void close() {
        LogFile f = logFile;
        if (f == null) return;

        logFile = null;
        f.close();
        overheadPrayers.clear();
        lastTiles.clear();
        log.debug("Event logger closed {}", f.getAbsolutePath());
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!TfaMistakeTrackerPlugin.CONFIG_GROUP.equals(event.getGroup())) return;
        if (!"eventLogger".equals(event.getKey())) return;

        if (shouldLog()) {
            open();
        } else {
            close();
        }
    }

    // Plugin events

    @Subscribe
    public void onInRaidChanged(InRaidChanged e) {
        write("IN_RAID_CHANGED", "inRaid=" + e.isInRaid());
    }

    @Subscribe
    public void onRaidRoomChanged(RaidRoomChanged e) {
        write("RAID_ROOM_CHANGED", "prev=" + e.getPrevRaidRoom() + " new=" + e.getNewRaidRoom());
    }

    @Subscribe
    public void onRaidEntered(RaidEntered e) {
        write("RAID_ENTERED", "raiders=" + e.getRaiderNames());
    }

    // Game events

    @Subscribe(priority = 10) // Before everything else, so the tick line comes first for this tick
    public void onGameTick(GameTick e) {
        if (!isLogging()) return;

        Player local = client.getLocalPlayer();
        WorldView wv = client.getTopLevelWorldView();
        String counts = wv == null ? "" :
                " players=" + wv.players().stream().count() + " npcs=" + wv.npcs().stream().count();
        if (local == null) {
            write("TICK", "local=null" + counts);
        } else {
            write("TICK", actorLocation(local) + " anim=" + local.getAnimation() + " pose=" + local.getPoseAnimation()
                    + counts);
        }

        if (wv != null) {
            logOverheadPrayerChanges(wv);
            logMoves(wv);
        }

        LogFile f = logFile;
        if (f != null) {
            f.flush();
        }
    }

    private void logOverheadPrayerChanges(WorldView wv) {
        for (Player player : wv.players()) {
            if (player == null || player.getName() == null) continue;

            String name = player.getName();
            HeadIcon icon = player.getOverheadIcon();
            boolean firstSeen = !overheadPrayers.containsKey(name);
            HeadIcon prev = overheadPrayers.put(name, icon);
            if (firstSeen || prev != icon) {
                write("OVERHEAD_PRAYER", "player name=" + clean(Text.removeTags(name)) + " old="
                        + (firstSeen ? "UNSEEN" : prayerName(prev)) + " new=" + prayerName(icon));
            }
        }
    }

    private void logMoves(WorldView wv) {
        if (!raidState.isInRaid()) {
            lastTiles.clear();
            return;
        }

        for (Player player : wv.players()) {
            if (player != null && player.getName() != null) {
                logMove("p:" + player.getName(), player);
            }
        }
        for (NPC npc : wv.npcs()) {
            if (npc != null) {
                logMove("n:" + npc.getIndex(), npc);
            }
        }
    }

    private void logMove(String key, Actor actor) {
        WorldPoint tile = actor.getWorldLocation();
        if (tile == null || tile.equals(lastTiles.put(key, tile))) return;

        LocalPoint lp = LocalPoint.fromWorld(client.getTopLevelWorldView(), tile);
        write("MOVE", actor(actor) + " " + localLocation(lp, tile.getPlane()));
    }

    private static String prayerName(HeadIcon icon) {
        return icon == null ? "NONE" : icon.name();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged e) {
        write("GAME_STATE", "state=" + e.getGameState());
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned e) {
        write("NPC_SPAWNED", npc(e.getNpc()) + " " + actorLocation(e.getNpc()));
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned e) {
        write("NPC_DESPAWNED", npc(e.getNpc()) + " " + actorLocation(e.getNpc()));
    }

    @Subscribe
    public void onNpcChanged(NpcChanged e) {
        String oldId = e.getOld() == null ? "null" : String.valueOf(e.getOld().getId());
        write("NPC_CHANGED", "oldId=" + oldId + " " + npc(e.getNpc()) + " " + actorLocation(e.getNpc()));
    }

    @Subscribe
    public void onPlayerSpawned(PlayerSpawned e) {
        write("PLAYER_SPAWNED", actor(e.getPlayer()) + " " + actorLocation(e.getPlayer()));
    }

    @Subscribe
    public void onPlayerDespawned(PlayerDespawned e) {
        write("PLAYER_DESPAWNED", actor(e.getPlayer()));
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged e) {
        Actor a = e.getActor();
        if (a == null) return;
        write("ANIMATION", actor(a) + " anim=" + a.getAnimation() + " " + actorLocation(a));
    }

    @Subscribe
    public void onGraphicChanged(GraphicChanged e) {
        Actor a = e.getActor();
        if (a == null) return;

        StringBuilder ids = new StringBuilder();
        for (ActorSpotAnim spotAnim : a.getSpotAnims()) {
            if (ids.length() > 0) ids.append(',');
            ids.append(spotAnim.getId());
        }
        write("GRAPHIC", actor(a) + " spotAnims=[" + ids + "] " + actorLocation(a));
    }

    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated e) {
        GraphicsObject g = e.getGraphicsObject();
        int ticksUntilStart = (g.getStartCycle() - client.getGameCycle()) / TfaMistakeTrackerPlugin.CYCLES_PER_GAME_TICK;
        write("GRAPHICS_OBJECT", "id=" + g.getId() + " startCycle=" + g.getStartCycle()
                + " ticksUntilStart=" + ticksUntilStart + " " + localLocation(g.getLocation(), g.getLevel()));
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved e) {
        Projectile p = e.getProjectile();
        if (!loggedProjectiles.add(p)) return;

        WorldPoint targetPoint = p.getTargetPoint();
        write("PROJECTILE", "id=" + p.getId()
                + " source=[" + actor(p.getSourceActor()) + "]"
                + " target=[" + actor(p.getTargetActor()) + "]"
                + " targetPoint=[" + targetPoint(targetPoint) + "]"
                + " remainingTicks=" + (p.getRemainingCycles() / TfaMistakeTrackerPlugin.CYCLES_PER_GAME_TICK)
                + " " + localLocation(e.getPosition(), client.getTopLevelWorldView().getPlane()));
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned e) {
        if (!shouldLogObject(e.getGameObject())) return;
        write("GAME_OBJECT_SPAWNED", tileObject(e.getGameObject()));
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned e) {
        if (!shouldLogObject(e.getGameObject())) return;
        write("GAME_OBJECT_DESPAWNED", tileObject(e.getGameObject()));
    }

    @Subscribe
    public void onGroundObjectSpawned(GroundObjectSpawned e) {
        if (!shouldLogObject(e.getGroundObject())) return;
        write("GROUND_OBJECT_SPAWNED", tileObject(e.getGroundObject()));
    }

    @Subscribe
    public void onGroundObjectDespawned(GroundObjectDespawned e) {
        if (!shouldLogObject(e.getGroundObject())) return;
        write("GROUND_OBJECT_DESPAWNED", tileObject(e.getGroundObject()));
    }

    @Subscribe
    public void onWallObjectSpawned(WallObjectSpawned e) {
        if (!shouldLogObject(e.getWallObject())) return;
        write("WALL_OBJECT_SPAWNED", tileObject(e.getWallObject()));
    }

    @Subscribe
    public void onWallObjectDespawned(WallObjectDespawned e) {
        if (!shouldLogObject(e.getWallObject())) return;
        write("WALL_OBJECT_DESPAWNED", tileObject(e.getWallObject()));
    }

    @Subscribe
    public void onDecorativeObjectSpawned(DecorativeObjectSpawned e) {
        if (!shouldLogObject(e.getDecorativeObject())) return;
        write("DECORATIVE_OBJECT_SPAWNED", tileObject(e.getDecorativeObject()));
    }

    @Subscribe
    public void onDecorativeObjectDespawned(DecorativeObjectDespawned e) {
        if (!shouldLogObject(e.getDecorativeObject())) return;
        write("DECORATIVE_OBJECT_DESPAWNED", tileObject(e.getDecorativeObject()));
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied e) {
        Hitsplat h = e.getHitsplat();
        String prayer = e.getActor() instanceof Player
                ? " prayer=" + prayerName(((Player) e.getActor()).getOverheadIcon())
                : "";
        write("HITSPLAT", actor(e.getActor()) + " type=" + h.getHitsplatType() + " amount=" + h.getAmount()
                + " mine=" + h.isMine() + " others=" + h.isOthers() + prayer + " " + actorLocation(e.getActor()));
    }

    @Subscribe
    public void onActorDeath(ActorDeath e) {
        write("ACTOR_DEATH", actor(e.getActor()) + " " + actorLocation(e.getActor()));
    }

    @Subscribe
    public void onInteractingChanged(InteractingChanged e) {
        write("INTERACTING", "source=[" + actor(e.getSource()) + "] target=[" + actor(e.getTarget()) + "]");
    }

    @Subscribe
    public void onOverheadTextChanged(OverheadTextChanged e) {
        write("OVERHEAD_TEXT", actor(e.getActor()) + " text=" + clean(e.getOverheadText()));
    }

    @Subscribe
    public void onChatMessage(ChatMessage e) {
        write("CHAT", "type=" + e.getType() + " name=" + clean(e.getName()) + " message="
                + clean(Text.removeTags(e.getMessage())));
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged e) {
        write("VAR", "varbit=" + e.getVarbitId() + " varp=" + e.getVarpId() + " value=" + e.getValue());
    }

    @Subscribe
    public void onVarClientIntChanged(VarClientIntChanged e) {
        write("VARC_INT", "index=" + e.getIndex() + " value=" + client.getVarcIntValue(e.getIndex()));
    }

    @Subscribe
    public void onVarClientStrChanged(VarClientStrChanged e) {
        write("VARC_STR", "index=" + e.getIndex() + " value=" + clean(client.getVarcStrValue(e.getIndex())));
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded e) {
        write("WIDGET_LOADED", "group=" + e.getGroupId());
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed e) {
        write("WIDGET_CLOSED", "group=" + e.getGroupId() + " unload=" + e.isUnload());
    }

    @Subscribe
    public void onSoundEffectPlayed(SoundEffectPlayed e) {
        write("SOUND", "id=" + e.getSoundId() + " delay=" + e.getDelay() + " source=[" + actor(e.getSource()) + "]");
    }

    @Subscribe
    public void onAreaSoundEffectPlayed(AreaSoundEffectPlayed e) {
        LocalPoint lp = LocalPoint.fromScene(e.getSceneX(), e.getSceneY(), client.getTopLevelWorldView());
        write("AREA_SOUND", "id=" + e.getSoundId() + " range=" + e.getRange() + " delay=" + e.getDelay()
                + " source=[" + actor(e.getSource()) + "] "
                + localLocation(lp, client.getTopLevelWorldView().getPlane()));
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired e) {
        if (!config.eventLoggerScripts()) return;
        write("SCRIPT", "id=" + e.getScriptId());
    }

    // Formatting

    private void write(String event, String details) {
        LogFile f = logFile;
        if (f == null) return;
        f.write(client.getTickCount() + "\t" + event + "\t" + details);
    }

    private String npc(NPC npc) {
        return "id=" + npc.getId() + " name=" + clean(Text.removeTags(npc.getName())) + " index=" + npc.getIndex()
                + " size=" + (npc.getComposition() == null ? "?" : npc.getComposition().getSize());
    }

    private String actor(Actor actor) {
        if (actor == null) {
            return "null";
        } else if (actor instanceof NPC) {
            NPC npc = (NPC) actor;
            return "npc id=" + npc.getId() + " name=" + clean(Text.removeTags(npc.getName())) + " index=" + npc.getIndex();
        } else if (actor instanceof Player) {
            return "player name=" + clean(Text.removeTags(actor.getName()));
        }
        return "actor name=" + clean(Text.removeTags(actor.getName()));
    }

    private String actorLocation(Actor actor) {
        if (actor == null) return "loc=null";
        LocalPoint lp = actor.getLocalLocation();
        WorldPoint instance = actor.getWorldLocation();
        return localLocation(lp, instance == null ? client.getTopLevelWorldView().getPlane() : instance.getPlane());
    }

    /**
     * Only log objects in the raid. A room's objects spawn while loading, before the raid state changes, and the lobby
     * scene includes neighbouring raid regions, so outside the raid both the player and the object must be in a room.
     */
    private boolean shouldLogObject(TileObject object) {
        if (raidState.isInRaid()) return true;

        Player local = client.getLocalPlayer();
        if (local == null) return false;

        return isRaidRoom(local.getLocalLocation(), local.getWorldLocation().getPlane())
                && isRaidRoom(object.getLocalLocation(), object.getPlane());
    }

    private boolean isRaidRoom(LocalPoint lp, int plane) {
        if (lp == null) return false;

        WorldPoint template = WorldPoint.fromLocalInstance(client, lp, plane);
        RaidRoom room = RaidRoom.forLocation(template.getRegionID(), template.getPlane());
        return room != null && room != RaidRoom.LOBBY;
    }

    // The target point is in instance coords
    private String targetPoint(WorldPoint targetPoint) {
        if (targetPoint == null) return "null";

        LocalPoint lp = LocalPoint.fromWorld(client.getTopLevelWorldView(), targetPoint);
        if (lp == null) {
            // Not in the scene, so it can't be converted
            return "instance " + worldPoint(targetPoint);
        }
        return localLocation(lp, targetPoint.getPlane());
    }

    private String tileObject(TileObject object) {
        return "id=" + object.getId() + " " + localLocation(object.getLocalLocation(), object.getPlane());
    }

    /**
     * Format a local location as both template (fromLocalInstance) coords with region, and instance coords.
     */
    private String localLocation(LocalPoint lp, int plane) {
        if (lp == null) return "loc=null";

        WorldPoint template = WorldPoint.fromLocalInstance(client, lp, plane);
        WorldPoint instance = WorldPoint.fromLocal(client.getTopLevelWorldView(), lp.getX(), lp.getY(), plane);
        return worldPoint(template) + " ix=" + instance.getX() + " iy=" + instance.getY();
    }

    private static String worldPoint(WorldPoint wp) {
        return "region=" + wp.getRegionID() + " plane=" + wp.getPlane() + " x=" + wp.getX() + " y=" + wp.getY()
                + " rx=" + wp.getRegionX() + " ry=" + wp.getRegionY();
    }

    private static String clean(String s) {
        if (s == null) return "null";
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    /**
     * A single log file and the executor that owns it. All disk IO happens on the executor thread.
     */
    private static class LogFile {

        private final Filepath dir;
        private final Filepath file;
        @Getter
        private final String absolutePath;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();

        // Only accessed on the executor thread
        private BufferedWriter writer;
        private boolean openFailed;

        LogFile(Filepath dir, Filepath file) {
            this.dir = dir;
            this.file = file;
            this.absolutePath = Filepath.Unchecked.getPath(file).toAbsolutePath().toString();
        }

        // Only called on the executor thread. Opens the file on the first write so no empty files are created.
        private boolean ensureOpen() {
            if (writer != null) return true;
            if (openFailed) return false;
            try {
                dir.createDirectories();
                writer = file.openBufferedWriter();
                return true;
            } catch (IOException e) {
                log.error("Unable to open event log " + absolutePath, e);
                openFailed = true;
                return false;
            }
        }

        void write(String line) {
            executor.execute(() -> {
                if (!ensureOpen()) return;
                try {
                    writer.write(line);
                    writer.newLine();
                } catch (IOException e) {
                    log.error("Unable to write event log " + absolutePath, e);
                }
            });
        }

        void flush() {
            executor.execute(() -> {
                if (writer == null) return;
                try {
                    writer.flush();
                } catch (IOException e) {
                    log.error("Unable to flush event log " + absolutePath, e);
                }
            });
        }

        void close() {
            executor.execute(() -> {
                if (writer == null) return;
                try {
                    writer.close();
                } catch (IOException e) {
                    log.error("Unable to close event log " + absolutePath, e);
                }
                writer = null;
            });
            // Let the queued writes and the close finish, without blocking the caller
            executor.shutdown();
        }
    }
}
