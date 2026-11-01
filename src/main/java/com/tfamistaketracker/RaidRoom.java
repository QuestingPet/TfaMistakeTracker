package com.tfamistaketracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Region IDs from LlemonDuck/the-fractured-archive (BSD-2)
@RequiredArgsConstructor
public enum RaidRoom {

    LOBBY(16470),
    // The Nexus is on plane 2 of the Archive Guardian region
    NEXUS(16471, 2),
    ARCHIVE_GUARDIAN(16471),
    EZEKIEL(16214),
    BORK(16727),
    MOIA(16216),
    DREADNOTH(16728),
    DAGONHAI(16215),
    XARTH(16726),
    OCELLUS(16472),
    LOOT(16474),
    ;

    private static final int ANY_PLANE = -1;

    @Getter
    private final int regionId;

    // The plane this room is on, or ANY_PLANE if the room is the whole region
    @Getter
    private final int plane;

    RaidRoom(int regionId) {
        this(regionId, ANY_PLANE);
    }

    /**
     * Retrieve the room for the given template region and plane. Rooms on a specific plane take priority over rooms
     * that are the whole region.
     */
    public static RaidRoom forLocation(int region, int plane) {
        RaidRoom anyPlaneRoom = null;
        for (RaidRoom r : RaidRoom.values()) {
            if (r.getRegionId() != region) continue;

            if (r.getPlane() == plane) {
                return r;
            } else if (r.getPlane() == ANY_PLANE && anyPlaneRoom == null) {
                anyPlaneRoom = r;
            }
        }

        return anyPlaneRoom;
    }
}
