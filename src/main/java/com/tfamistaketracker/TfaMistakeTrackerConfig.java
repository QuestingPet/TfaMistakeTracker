package com.tfamistaketracker;

import net.runelite.api.annotations.VisibleForDevtools;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

@ConfigGroup(TfaMistakeTrackerPlugin.CONFIG_GROUP)
public interface TfaMistakeTrackerConfig extends Config {

    @ConfigItem(
            keyName = "showMistakesInChat",
            name = "Show Mistakes In Chat",
            description = "When a player makes a mistake in TFA, whether or not to log the mistake message to your " +
                    "public chat. Max message length of " + TfaMistake.MAX_MESSAGE_LENGTH + " to prevent spamming chat.",
            position = 0
    )
    default boolean showMistakesInChat() {
        return true;
    }

    @ConfigItem(
            keyName = "showMistakesOnOverheadText",
            name = "Show Mistakes On Overhead Text",
            description = "When a player makes a mistake in TFA, whether or not to show the mistake message above " +
                    "their head as overhead text.",
            position = 1
    )
    default boolean showMistakesOnOverheadText() {
        return true;
    }

    @ConfigItem(
            keyName = "mistakeMessageStacking",
            name = "Mistake Message Stacking",
            description = "When a player makes a mistake in TFA and the message is set to \"?\", how to handle " +
                    "repeated mistakes for the current raid.",
            position = 2
    )
    default StackingBehavior mistakeMessageStacking() {
        return StackingBehavior.SAME_MISTAKES_ONLY;
    }

    @ConfigSection(
            name = "Death Messages",
            description = "Settings for the messages shown on Death mistakes (separate multiple messages by \"|\").",
            position = 3,
            closedByDefault = true
    )
    String deathMistakeSettings = "deathMistakeSettings";

    @ConfigItem(
            keyName = "deathMessage",
            name = "Death",
            description = "Message to show on death.",
            section = deathMistakeSettings,
            position = 0
    )
    default String deathMessage() {
        return TfaMistake.defaultDeathMessage();
    }

    @ConfigSection(
            name = "Archive Guardian Mistake Messages",
            description = "Settings for the messages shown on Archive Guardian mistakes (separate multiple messages by \"|\").",
            position = 4,
            closedByDefault = true
    )
    String archiveGuardianMistakeSettings = "archiveGuardianMistakeSettings";

    @ConfigItem(
            keyName = "archiveGuardianSlamMessage",
            name = "Archive Guardian Slam",
            description = "Message to show when hit by the Archive Guardian's slam when he drops down.",
            section = archiveGuardianMistakeSettings,
            position = 0
    )
    default String archiveGuardianSlamMessage() {
        return TfaMistake.ARCHIVE_GUARDIAN_SLAM.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "archiveGuardianMeleeMessage",
            name = "Archive Guardian Melee",
            description = "Message to show when hit by the Archive Guardian's melee attack while under him.",
            section = archiveGuardianMistakeSettings,
            position = 1
    )
    default String archiveGuardianMeleeMessage() {
        return TfaMistake.ARCHIVE_GUARDIAN_MELEE.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "archiveGuardianRangeMessage",
            name = "Archive Guardian Range",
            description = "Message to show when hit by the Archive Guardian's rock without praying range.",
            section = archiveGuardianMistakeSettings,
            position = 2
    )
    default String archiveGuardianRangeMessage() {
        return TfaMistake.ARCHIVE_GUARDIAN_RANGE.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "archiveGuardianMushroomMessage",
            name = "Archive Guardian Mushroom",
            description = "Message to show when caught in a mushroom explosion in the Archive Guardian room.",
            section = archiveGuardianMistakeSettings,
            position = 3
    )
    default String archiveGuardianMushroomMessage() {
        return TfaMistake.ARCHIVE_GUARDIAN_MUSHROOM.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "archiveGuardianVineMessage",
            name = "Archive Guardian Vine",
            description = "Message to show when hit by a vine in the Archive Guardian room.",
            section = archiveGuardianMistakeSettings,
            position = 4
    )
    default String archiveGuardianVineMessage() {
        return TfaMistake.ARCHIVE_GUARDIAN_VINE.getDefaultMessage();
    }

    // DEBUG ONLY -- everything in this section is for development and must be removed before publishing
    @ConfigSection(
            name = "Debug",
            description = "Developer tools. Only work in developer mode.",
            position = 5,
            closedByDefault = true
    )
    String debugSettings = "debugSettings";

    @VisibleForDevtools
    @ConfigItem(
            keyName = "debugMode",
            name = "Debug Mode",
            description = "Show the debug overlay. Only works in developer mode.",
            section = debugSettings,
            position = 0
    )
    default boolean debugMode() {
        return false;
    }

    @VisibleForDevtools
    @ConfigItem(
            keyName = "eventLogger",
            name = "Event Logger",
            description = "Log game events and their ids to debug-logs/ in the working directory. Only works in " +
                    "developer mode.",
            section = debugSettings,
            position = 1
    )
    default boolean eventLogger() {
        return false;
    }

    @VisibleForDevtools
    @ConfigItem(
            keyName = "eventLoggerScripts",
            name = "Event Logger Scripts",
            description = "Also log every script that runs. Very noisy.",
            section = debugSettings,
            position = 2
    )
    default boolean eventLoggerScripts() {
        return false;
    }

    @VisibleForDevtools
    @ConfigItem(
            keyName = "eventLoggerMarkerKey",
            name = "Event Logger Marker Key",
            description = "Press to write a numbered marker line into the event log.",
            section = debugSettings,
            position = 3
    )
    default Keybind eventLoggerMarkerKey() {
        return Keybind.NOT_SET;
    }

    @VisibleForDevtools
    @ConfigItem(
            keyName = "resetDetectors",
            name = "Reset Detectors",
            description = "Toggle to recreate all mistake detectors, for picking up hotswapped changes.",
            section = debugSettings,
            position = 4
    )
    default boolean resetDetectors() {
        return false;
    }
}
