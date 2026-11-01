package com.tfamistaketracker;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.tfamistaketracker.events.InRaidChanged;
import com.tfamistaketracker.events.RaidEntered;
import com.tfamistaketracker.events.RaidRoomChanged;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tfamistaketracker.RaidRoom.LOBBY;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RaidState {

    // TFA HUD scripts, analogous to ToA's HUD init script (6585). 10034 rebuilds the HUD party names, 10037 builds
    // the HUD orbs. Either one running means the raider name varcs may have changed.
    private static final int TFA_HUD_NAMES_SCRIPT_ID = 10034;
    private static final int TFA_HUD_ORBS_SCRIPT_ID = 10037;

    private static final List<Integer> TFA_RAIDER_NAME_VARCS = ImmutableList.of(
            VarClientID.TFA_CLIENT_NAME0,
            VarClientID.TFA_CLIENT_NAME1,
            VarClientID.TFA_CLIENT_NAME2,
            VarClientID.TFA_CLIENT_NAME3,
            VarClientID.TFA_CLIENT_NAME4,
            VarClientID.TFA_CLIENT_NAME5,
            VarClientID.TFA_CLIENT_NAME6,
            VarClientID.TFA_CLIENT_NAME7
    );
    private static final Set<Integer> TFA_RAIDER_NAME_VARCS_SET = ImmutableSet.copyOf(TFA_RAIDER_NAME_VARCS);

    // Keep inRaid true for a few ticks after leaving, to cover any gaps between rooms
    private static final int RAID_LEAVE_GRACE_TICKS = 3;

    private final Client client;
    private final EventBus eventBus;

    @Getter
    private boolean inRaid;
    @Getter
    private RaidRoom currentRoom;
    @Getter
    private final Map<String, Raider> raiders = new HashMap<>(); // name -> raider
    @Getter
    private int raidLeaveTicks;

    private int prevRegion;
    private int prevPlane;
    private boolean newRaid;
    // Set when the party names may have changed. The raiders are then updated on the next GameTick, once all the name
    // varcs have settled (the game writes them one at a time).
    private boolean raidersDirty;

    public void startUp() {
        clearState();
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
        clearState();
    }

    private void clearState() {
        inRaid = false;
        currentRoom = null;
        raiders.clear();
        raidLeaveTicks = 0;
        prevRegion = -1;
        prevPlane = -1;
        newRaid = false;
        raidersDirty = false;
    }

    @Subscribe(priority = 5)
    public void onGameTick(GameTick e) {
        if (client.getGameState() != GameState.LOGGED_IN) return;

        WorldPoint location = getTemplateLocation();
        if (location == null) return;

        int newRegion = location.getRegionID();
        int newPlane = location.getPlane();
        if (prevRegion != newRegion || prevPlane != newPlane) {
            locationChanged(newRegion, newPlane);
        }
        prevRegion = newRegion;
        prevPlane = newPlane;

        updateInRaid();

        if (!inRaid) {
            raiders.clear();
            return;
        }

        // If the party may have changed, or we still haven't loaded all raiders, update them. Not all raiders may be
        // loaded if the plugin is turned on mid-raid, or if the players aren't in the scene yet.
        if (raidersDirty || !isAllRaidersLoaded(getRaiderNames())) {
            updateRaiders();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOADING) {
            // If there are still raiders, they can't be dead anymore after loading.
            for (Raider raider : raiders.values()) {
                raider.setDead(false);
            }
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event) {
        if (!inRaid) return;

        if (event.getScriptId() == TFA_HUD_NAMES_SCRIPT_ID || event.getScriptId() == TFA_HUD_ORBS_SCRIPT_ID) {
            raidersDirty = true;
        }
    }

    @Subscribe
    public void onVarClientStrChanged(VarClientStrChanged event) {
        if (!inRaid) return;

        if (TFA_RAIDER_NAME_VARCS_SET.contains(event.getIndex())) {
            raidersDirty = true;
        }
    }

    @Subscribe
    public void onPlayerSpawned(PlayerSpawned event) {
        // Players get new objects after a loading screen (e.g. between rooms), so replace any stale raider. Death state
        // is already reset on loading, and previous locations are no longer valid.
        Player player = event.getPlayer();
        if (player.getName() != null && raiders.containsKey(player.getName())) {
            log.debug("Raider respawned: {}", player.getName());
            raiders.put(player.getName(), new Raider(player));
        }
    }

    public boolean isRaider(Actor actor) {
        return raiders.containsKey(actor.getName());
    }

    /**
     * @return The sanitized names of all raiders in the party, as read from the TFA HUD varcs
     */
    public Set<String> getRaiderNames() {
        Set<String> raiderNames = new HashSet<>(TFA_RAIDER_NAME_VARCS.size());
        for (int varc : TFA_RAIDER_NAME_VARCS) {
            String name = client.getVarcStrValue(varc);
            if (name != null && !name.isEmpty()) {
                raiderNames.add(Text.sanitize(name));
            }
        }
        return raiderNames;
    }

    private WorldPoint getTemplateLocation() {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) return null;

        LocalPoint localPoint = localPlayer.getLocalLocation();
        if (localPoint == null) {
            return null;
        } else {
            return WorldPoint.fromLocalInstance(client, localPoint);
        }
    }

    private void locationChanged(int newRegion, int newPlane) {
        RaidRoom prevRoom = currentRoom;
        currentRoom = RaidRoom.forLocation(newRegion, newPlane);
        if (currentRoom == prevRoom) return;

        // currentRoom may be null while still in the raid, in any area we don't know about yet
        log.debug("Raid room changed: {}", currentRoom);
        eventBus.post(RaidRoomChanged.builder().newRaidRoom(currentRoom).prevRaidRoom(prevRoom).build());

        // TODO: RaidFinished?
    }

    private void updateInRaid() {
        boolean inRaidRaw = (currentRoom != null && currentRoom != LOBBY) || isRaidHudVisible();
        raidLeaveTicks = inRaidRaw ? RAID_LEAVE_GRACE_TICKS : Math.max(raidLeaveTicks - 1, 0);

        boolean newInRaid = raidLeaveTicks > 0;
        if (newInRaid == inRaid) return;

        log.debug("In Raid changed: {}", newInRaid);
        inRaid = newInRaid;
        if (inRaid) {
            // We can't load the raiders yet in case they're not in the scene or varcs. updateRaiders will post
            // RaidEntered once they're loaded.
            newRaid = true;
        } else {
            newRaid = false;
        }
        eventBus.post(new InRaidChanged(inRaid));
    }

    private boolean isRaidHudVisible() {
        Widget hud = client.getWidget(InterfaceID.TfaHud.UNIVERSE);
        return hud != null && !hud.isHidden();
    }

    /**
     * Update the loaded raiders to match the party names in the varcs. Raiders that are still in the party keep their
     * existing {@link Raider} (and its state), only the differences are added or removed.
     */
    private void updateRaiders() {
        raidersDirty = false;

        Set<String> raiderNames = getRaiderNames();
        WorldView worldView = client.getTopLevelWorldView();
        if (raiderNames.isEmpty() || worldView == null) return;

        // Remove raiders no longer in the party
        boolean changed = raiders.keySet().retainAll(raiderNames);

        // Add any raiders in the party that aren't loaded yet
        for (Player player : worldView.players()) {
            if (player != null &&
                    player.getName() != null &&
                    !raiders.containsKey(player.getName()) &&
                    raiderNames.contains(player.getName())) {
                raiders.put(player.getName(), new Raider(player));
                changed = true;
            }
        }

        if (changed) {
            log.debug("Raiders changed: {} (party: {})", raiders.keySet(), raiderNames);
        }

        if (!isAllRaidersLoaded(raiderNames)) {
            return; // Will try again next tick
        }

        if (newRaid) {
            log.debug("New raid");
            eventBus.post(new RaidEntered(ImmutableList.copyOf(raiders.keySet())));
            newRaid = false;
        }
    }

    private boolean isAllRaidersLoaded(Set<String> raiderNames) {
        return !raiderNames.isEmpty() && raiders.keySet().containsAll(raiderNames);
    }
}
