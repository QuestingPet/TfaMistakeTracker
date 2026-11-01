package com.tfamistaketracker.mistakestate;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;

/**
 * Reads MistakeState from disk
 */
@Slf4j
@Singleton
public class MistakeStateReader {

    private final Gson gson;
    private final MistakeStateWriter mistakeStateWriter;

    @Inject
    public MistakeStateReader(Gson gson, MistakeStateWriter mistakeStateWriter) {
        this.gson = gson;
        this.mistakeStateWriter = mistakeStateWriter;
    }

    public MistakeStateManager read() {
        // The writer owns the file location, which is set from the plugin directory on startup
        final Filepath mistakeStateFilePath = mistakeStateWriter.getMistakeStateFilePath();
        if (mistakeStateFilePath.exists()) {
            try (BufferedReader reader = mistakeStateFilePath.openBufferedReader();
                 JsonReader jsonReader = new JsonReader(reader)) {
                MistakeStateManager mistakeStateManager = gson.fromJson(jsonReader, MistakeStateManager.class);
                if (mistakeStateManager == null) {
                    return new MistakeStateManager(mistakeStateWriter);
                }
                mistakeStateManager.setMistakeStateWriter(mistakeStateWriter);
                return mistakeStateManager;
            } catch (IOException | JsonSyntaxException e) {
                log.error("Unable to read mistake state from " + mistakeStateFilePath, e);
            }
        }

        return new MistakeStateManager(mistakeStateWriter);
    }
}
