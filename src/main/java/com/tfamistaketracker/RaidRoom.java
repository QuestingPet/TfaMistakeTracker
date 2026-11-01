package com.tfamistaketracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum RaidRoom {

    // TODO: Add TFA rooms and their region IDs once they're known (use the debug overlay to read them in game)
    ;

    @Getter
    private final int regionId;

    public static RaidRoom forRegionId(int region) {
        for (RaidRoom r : RaidRoom.values()) {
            if (r.getRegionId() == region) {
                return r;
            }
        }

        return null;
    }
}
