package com.tfamistaketracker.mistakestate;

import net.runelite.client.util.Filepath;

public class MistakeStateUtil {

    // Use the same mistake state file regardless of user
    private static final String MISTAKE_STATE_FILE_NAME = "mistake-state.txt";

    private static final String DEVELOPER_MODE_PREFIX = "dev-";

    static Filepath getMistakeStateFilePath(Filepath pluginDirectory, boolean developerMode) {
        final String filename;
        if (developerMode) {
            filename = DEVELOPER_MODE_PREFIX + MISTAKE_STATE_FILE_NAME;
        } else {
            filename = MISTAKE_STATE_FILE_NAME;
        }

        return pluginDirectory.joinSegment(filename);
    }
}
