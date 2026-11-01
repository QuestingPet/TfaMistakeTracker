package com.tfamistaketracker;

import com.google.common.collect.ImmutableList;
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
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RaidState {

    private final Client client;
    private final EventBus eventBus;

    @Getter
    private boolean inRaid;
    @Getter
    private RaidRoom currentRoom;
    @Getter
    private final Map<String, Raider> raiders = new HashMap<>(); // name -> raider

    private int prevRegion;
    private boolean newRaid;

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
        prevRegion = -1;
        newRaid = false;
    }

    @Subscribe(priority = 5)
    public void onGameTick(GameTick e) {
        if (client.getGameState() != GameState.LOGGED_IN) return;

        int newRegion = getRegion();
        if (newRegion == -1) return;

        if (prevRegion != newRegion) {
            regionChanged(newRegion);
        }
        prevRegion = newRegion;

        if (!inRaid) {
            raiders.clear();
            return;
        }

        // If we still haven't loaded any raiders, keep trying. This can happen if the plugin is turned on mid-raid,
        // or if the relevant players aren't actually in the raid yet for the client to retrieve.
        if (raiders.isEmpty()) {
            tryLoadRaiders();
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

    public boolean isRaider(Actor actor) {
        return raiders.containsKey(actor.getName());
    }

    private int getRegion() {
        LocalPoint localPoint = client.getLocalPlayer().getLocalLocation();
        if (localPoint == null) {
            return -1;
        } else {
            return WorldPoint.fromLocalInstance(client, localPoint).getRegionID();
        }
    }

    private void regionChanged(int newRegion) {
        RaidRoom prevRoom = currentRoom;
        currentRoom = RaidRoom.forRegionId(newRegion);

        // TODO: Once TFA rooms are known, decide which rooms count as in raid (ToA excluded the outside lobby) and
        // which room transition denotes a new raid (ToA used outside lobby -> inside lobby).
        boolean newInRaid = currentRoom != null;
        if (newInRaid != inRaid) {
            log.debug("In Raid changed: {}", newInRaid);
            eventBus.post(new InRaidChanged(newInRaid));
            newRaid = newInRaid;
        }
        inRaid = newInRaid;

        if (currentRoom == null || currentRoom == prevRoom) {
            return;
        }

        log.debug("Raid room changed: {}", currentRoom);
        eventBus.post(RaidRoomChanged.builder().newRaidRoom(currentRoom).prevRaidRoom(prevRoom).build());

        // TODO: RaidFinished?
    }

    private void tryLoadRaiders() {
        Set<String> raiderNames = getRaiderNames();
        if (raiderNames.isEmpty()) return;

        log.debug("Setting raiders");
        raiders.clear();

        for (Player player : client.getPlayers()) {
            if (player != null &&
                    player.getName() != null &&
                    !raiders.containsKey(player.getName()) &&
                    raiderNames.contains(player.getName())) {
                raiders.put(player.getName(), new Raider(player));
            }
        }

        log.debug("Loaded raiderNames: {}", raiderNames);
        log.debug("Loaded raiders: {}", raiders.keySet());

        if (raiders.isEmpty() || raiders.size() < raiderNames.size()) {
            log.debug("Not enough raiders loaded. Will try again later...");
            raiders.clear();
            return;
        }

        if (newRaid) {
            log.debug("New raid");
            eventBus.post(new RaidEntered(ImmutableList.copyOf(raiders.keySet())));
            newRaid = false;
        }
    }

    private Set<String> getRaiderNames() {
        // TODO: Find where TFA exposes the party member names (ToA read them from varcs set by its HUD init
        // script). Names should be sanitized with Text.sanitize. Until then no raiders are ever loaded.
        return Collections.emptySet();
    }
}
