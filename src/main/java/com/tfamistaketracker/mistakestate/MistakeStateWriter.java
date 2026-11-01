package com.tfamistaketracker.mistakestate;

import com.google.gson.Gson;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.BufferedWriter;
import java.io.IOException;

/**
 * Writes MistakeState to disk
 */
@Slf4j
@Singleton
public class MistakeStateWriter {

    private final Gson gson;
    private final boolean developerMode;

    private Filepath mistakeStateDir;
    @Getter(AccessLevel.PACKAGE)
    private Filepath mistakeStateFilePath;

    @Inject
    public MistakeStateWriter(Gson gson, @Named("developerMode") boolean developerMode) {
        this.gson = gson;
        this.developerMode = developerMode;
    }

    /**
     * Set the plugin data directory that the mistake state is read from and written to. This must be called before
     * any reads or writes.
     *
     * @param pluginDirectory The plugin data directory
     */
    public void setPluginDirectory(Filepath pluginDirectory) {
        this.mistakeStateDir = pluginDirectory;
        this.mistakeStateFilePath = MistakeStateUtil.getMistakeStateFilePath(pluginDirectory, developerMode);
    }

    public void write(MistakeStateManager mistakeStateManager) {
        try {
            mistakeStateDir.createDirectories();
        } catch (IOException e) {
            log.error("Unable to create directories " + mistakeStateDir, e);
            return;
        }

        final Filepath filepath = mistakeStateFilePath;
        try (BufferedWriter writer = filepath.openBufferedWriter()) {
            writer.write(gson.toJson(mistakeStateManager));
        } catch (IOException e) {
            log.error("Unable to write mistake state to " + filepath, e);
        }
    }
}
