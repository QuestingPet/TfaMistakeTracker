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

    @ConfigItem(
            keyName = TfaMistakeTrackerPlugin.SHOW_SIDE_PANEL_CONFIG_KEY,
            name = "Show Side Panel",
            description = "Whether or not to show the Tfa Mistake Tracker side panel. Mistakes are still tracked " +
                    "while the side panel is hidden.",
            position = 3
    )
    default boolean showSidePanel() {
        return true;
    }

    @ConfigSection(
            name = "Death Messages",
            description = "Settings for the messages shown on Death mistakes (separate multiple messages by \"|\").",
            position = 4,
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
            position = 5,
            closedByDefault = true
    )
    String archiveGuardianMistakeSettings = "archiveGuardianMistakeSettings";

    @ConfigItem(
            keyName = "archiveGuardianFallMessage",
            name = "Archive Guardian Fall",
            description = "Message to show when hit by the Archive Guardian falling down.",
            section = archiveGuardianMistakeSettings,
            position = 0
    )
    default String archiveGuardianFallMessage() {
        return TfaMistake.ARCHIVE_GUARDIAN_FALL.getDefaultMessage();
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

    @ConfigSection(
            name = "Bork Mistake Messages",
            description = "Settings for the messages shown on Bork mistakes (separate multiple messages by \"|\").",
            position = 6,
            closedByDefault = true
    )
    String borkMistakeSettings = "borkMistakeSettings";

    @ConfigItem(
            keyName = "borkStompMessage",
            name = "Bork Stomp",
            description = "Message to show when hit by a wave from Bork's stomp.",
            section = borkMistakeSettings,
            position = 0
    )
    default String borkStompMessage() {
        return TfaMistake.BORK_STOMP.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "borkMeleeMessage",
            name = "Bork Melee",
            description = "Message to show when hit by Bork's melee attack without praying melee.",
            section = borkMistakeSettings,
            position = 1
    )
    default String borkMeleeMessage() {
        return TfaMistake.BORK_MELEE.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "borkSmorksAaMessage",
            name = "Bork Smorks Auto Attack",
            description = "Message to show when hit by The Smorks' ranged or magic attack without the matching prayer.",
            section = borkMistakeSettings,
            position = 2
    )
    default String borkSmorksAaMessage() {
        return TfaMistake.BORK_SMORKS_AA.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "borkFireMessage",
            name = "Bork Fire",
            description = "Message to show when standing on fire in the Bork room.",
            section = borkMistakeSettings,
            position = 3
    )
    default String borkFireMessage() {
        return TfaMistake.BORK_FIRE.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "borkFireballMessage",
            name = "Bork Fireball",
            description = "Message to show when hit by The Smorks' fireball landing.",
            section = borkMistakeSettings,
            position = 4
    )
    default String borkFireballMessage() {
        return TfaMistake.BORK_FIREBALL.getDefaultMessage();
    }

    @ConfigSection(
            name = "Dreadnoth Mistake Messages",
            description = "Settings for the messages shown on Dreadnoth mistakes (separate multiple messages by \"|\").",
            position = 7,
            closedByDefault = true
    )
    String dreadnothMistakeSettings = "dreadnothMistakeSettings";

    @ConfigItem(
            keyName = "dreadnothSlamMessage",
            name = "Dreadnoth Slam",
            description = "Message to show when hit by Dreadnoth's jump slam without praying melee.",
            section = dreadnothMistakeSettings,
            position = 0
    )
    default String dreadnothSlamMessage() {
        return TfaMistake.DREADNOTH_SLAM.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "dreadnothRangeMessage",
            name = "Dreadnoth Range",
            description = "Message to show when hit by Dreadnoth's ranged attack without praying range.",
            section = dreadnothMistakeSettings,
            position = 1
    )
    default String dreadnothRangeMessage() {
        return TfaMistake.DREADNOTH_RANGE.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "dreadnothBloodPoolMessage",
            name = "Dreadnoth Blood Pool",
            description = "Message to show when standing in a blood pool in the Dreadnoth room.",
            section = dreadnothMistakeSettings,
            position = 2
    )
    default String dreadnothBloodPoolMessage() {
        return TfaMistake.DREADNOTH_BLOOD_POOL.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "dreadnothBloodBallMessage",
            name = "Dreadnoth Blood Ball",
            description = "Message to show when hit by one of Dreadnoth's blood balls landing.",
            section = dreadnothMistakeSettings,
            position = 3
    )
    default String dreadnothBloodBallMessage() {
        return TfaMistake.DREADNOTH_BLOOD_BALL.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "dreadnothLandedMessage",
            name = "Dreadnoth Landed",
            description = "Message to show when Dreadnoth lands on you.",
            section = dreadnothMistakeSettings,
            position = 4
    )
    default String dreadnothLandedMessage() {
        return TfaMistake.DREADNOTH_LANDED.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "dreadnothJellyMessage",
            name = "Dreadnoth Jelly",
            description = "Message to show when hit by a jelly without praying melee.",
            section = dreadnothMistakeSettings,
            position = 5
    )
    default String dreadnothJellyMessage() {
        return TfaMistake.DREADNOTH_JELLY.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "dreadnothInsideMessage",
            name = "Dreadnoth Inside",
            description = "Message to show when standing inside Dreadnoth.",
            section = dreadnothMistakeSettings,
            position = 6
    )
    default String dreadnothInsideMessage() {
        return TfaMistake.DREADNOTH_INSIDE.getDefaultMessage();
    }

    @ConfigSection(
            name = "Xarth Mistake Messages",
            description = "Settings for the messages shown on Xarth mistakes (separate multiple messages by \"|\").",
            position = 8,
            closedByDefault = true
    )
    String xarthMistakeSettings = "xarthMistakeSettings";

    @ConfigItem(
            keyName = "xarthTetherMessage",
            name = "Xarth Tether",
            description = "Message to show when hit by Xarth's tether fireball.",
            section = xarthMistakeSettings,
            position = 0
    )
    default String xarthTetherMessage() {
        return TfaMistake.XARTH_TETHER.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "xarthExplosionMessage",
            name = "Xarth Explosion",
            description = "Message to show when caught in Xarth's flame blast explosion.",
            section = xarthMistakeSettings,
            position = 1
    )
    default String xarthExplosionMessage() {
        return TfaMistake.XARTH_EXPLOSION.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "xarthFireMessage",
            name = "Xarth Fire",
            description = "Message to show when standing in fire in the Xarth room.",
            section = xarthMistakeSettings,
            position = 2
    )
    default String xarthFireMessage() {
        return TfaMistake.XARTH_FIRE.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "xarthMeleeMessage",
            name = "Xarth Melee",
            description = "Message to show when hit by Xarth's melee attack without praying melee.",
            section = xarthMistakeSettings,
            position = 3
    )
    default String xarthMeleeMessage() {
        return TfaMistake.XARTH_MELEE.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "xarthCorpseHealMessage",
            name = "Xarth Corpse Heal",
            description = "Message to show when a corpse reaches Xarth and heals him. Every raider gets this mistake.",
            section = xarthMistakeSettings,
            position = 4
    )
    default String xarthCorpseHealMessage() {
        return TfaMistake.XARTH_CORPSE_HEAL.getDefaultMessage();
    }

    @ConfigSection(
            name = "Ocellus P1 Mistake Messages",
            description = "Settings for the messages shown on Ocellus phase 1 mistakes (separate multiple messages by \"|\").",
            position = 9,
            closedByDefault = true
    )
    String ocellusP1MistakeSettings = "ocellusP1MistakeSettings";

    @ConfigItem(
            keyName = "ocellusP1AutoAttackMessage",
            name = "Ocellus P1 Auto Attack",
            description = "Message to show when hit by Ocellus' magic or ranged attack in phase 1 without the matching prayer.",
            section = ocellusP1MistakeSettings,
            position = 0
    )
    default String ocellusP1AutoAttackMessage() {
        return TfaMistake.OCELLUS_P1_AUTO_ATTACK.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "ocellusP1SlamMessage",
            name = "Ocellus P1 Slam",
            description = "Message to show when caught in Ocellus' slam in phase 1.",
            section = ocellusP1MistakeSettings,
            position = 1
    )
    default String ocellusP1SlamMessage() {
        return TfaMistake.OCELLUS_P1_SLAM.getDefaultMessage();
    }

    @ConfigSection(
            name = "Ocellus P2 Mistake Messages",
            description = "Settings for the messages shown on Ocellus phase 2 mistakes (separate multiple messages by \"|\").",
            position = 10,
            closedByDefault = true
    )
    String ocellusP2MistakeSettings = "ocellusP2MistakeSettings";

    @ConfigItem(
            keyName = "ocellusP2PortalMessage",
            name = "Ocellus P2 Portal",
            description = "Message to show when hit by a portal projectile in Ocellus phase 2.",
            section = ocellusP2MistakeSettings,
            position = 0
    )
    default String ocellusP2PortalMessage() {
        return TfaMistake.OCELLUS_P2_PORTAL.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "ocellusP2PurpleTileMessage",
            name = "Ocellus P2 Purple Tile",
            description = "Message to show when standing on a purple tile when it explodes in Ocellus phase 2.",
            section = ocellusP2MistakeSettings,
            position = 1
    )
    default String ocellusP2PurpleTileMessage() {
        return TfaMistake.OCELLUS_P2_PURPLE_TILE.getDefaultMessage();
    }

    @ConfigSection(
            name = "Ocellus P3 Mistake Messages",
            description = "Settings for the messages shown on Ocellus phase 3 mistakes (separate multiple messages by \"|\").",
            position = 11,
            closedByDefault = true
    )
    String ocellusP3MistakeSettings = "ocellusP3MistakeSettings";

    @ConfigItem(
            keyName = "ocellusP3VoidGriefMessage",
            name = "Ocellus P3 Void Grief",
            description = "Message to show when hit by a void bomb or standing on the void floor it leaves in Ocellus phase 3.",
            section = ocellusP3MistakeSettings,
            position = 0
    )
    default String ocellusP3VoidGriefMessage() {
        return TfaMistake.OCELLUS_P3_VOID_GRIEF.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "ocellusP3AoeMessage",
            name = "Ocellus P3 AOE",
            description = "Message to show when hit by Ocellus' shadow slash (AOE auto-attack) in phase 3.",
            section = ocellusP3MistakeSettings,
            position = 1
    )
    default String ocellusP3AoeMessage() {
        return TfaMistake.OCELLUS_P3_AOE.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "ocellusP3InsolenceMessage",
            name = "Ocellus P3 Insolence",
            description = "Message to show when hit by Ocellus' superior slash (Insolence) in phase 3.",
            section = ocellusP3MistakeSettings,
            position = 2
    )
    default String ocellusP3InsolenceMessage() {
        return TfaMistake.OCELLUS_P3_INSOLENCE.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "ocellusP3MeleeParryMessage",
            name = "Ocellus P3 Melee Parry",
            description = "Message to show when hit during Ocellus' melee parry in phase 3 without praying melee.",
            section = ocellusP3MistakeSettings,
            position = 3
    )
    default String ocellusP3MeleeParryMessage() {
        return TfaMistake.OCELLUS_P3_MELEE_PARRY.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "ocellusP3TankMageMessage",
            name = "Ocellus P3 Tank Mage",
            description = "Message to show when Ocellus hits everyone with magic in phase 3 because the tank wasn't in melee range.",
            section = ocellusP3MistakeSettings,
            position = 4
    )
    default String ocellusP3TankMageMessage() {
        return TfaMistake.OCELLUS_P3_TANK_MAGE.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "ocellusP3VoidFlareDetonationMessage",
            name = "Ocellus P3 Void Flare Detonation",
            description = "Message to show when a void rift, siphon, or augmenter detonates in Ocellus phase 3. Every raider gets this mistake.",
            section = ocellusP3MistakeSettings,
            position = 5
    )
    default String ocellusP3VoidFlareDetonationMessage() {
        return TfaMistake.OCELLUS_P3_VOID_FLARE_DETONATION.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "ocellusP3UntetherMessage",
            name = "Ocellus P3 Untether",
            description = "Message to show when caught in Ocellus' untether explosion in phase 3.",
            section = ocellusP3MistakeSettings,
            position = 6
    )
    default String ocellusP3UntetherMessage() {
        return TfaMistake.OCELLUS_P3_UNTETHER.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "ocellusP3FallMessage",
            name = "Ocellus P3 Fall",
            description = "Message to show when falling into the abyss in Ocellus phase 3.",
            section = ocellusP3MistakeSettings,
            position = 7
    )
    default String ocellusP3FallMessage() {
        return TfaMistake.OCELLUS_P3_FALL.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "ocellusP4VoidExplosionMessage",
            name = "Ocellus P4 Void Explosion",
            description = "Message to show when caught by a void explosion chasing you after Ocellus' siphon in phase 4.",
            section = ocellusP3MistakeSettings,
            position = 8
    )
    default String ocellusP4VoidExplosionMessage() {
        return TfaMistake.OCELLUS_P4_VOID_EXPLOSION.getDefaultMessage();
    }

    @ConfigItem(
            keyName = "ocellusP4PrayerAttackMessage",
            name = "Ocellus P4 Prayer Attack",
            description = "Message to show when hit by Ocellus' prayer attack in phase 4 without the matching prayer.",
            section = ocellusP3MistakeSettings,
            position = 9
    )
    default String ocellusP4PrayerAttackMessage() {
        return TfaMistake.OCELLUS_P4_PRAYER_ATTACK.getDefaultMessage();
    }

    // DEBUG ONLY -- everything in this section is for development and must be removed before publishing
    @ConfigSection(
            name = "Debug",
            description = "Developer tools. Only work in developer mode.",
            position = 12,
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
            keyName = "debugTiles",
            name = "Debug Tiles",
            description = "Show the hit tiles, hazards and projectiles the detectors are tracking. Only works in " +
                    "developer mode.",
            section = debugSettings,
            position = 1
    )
    default boolean debugTiles() {
        return false;
    }

    @VisibleForDevtools
    @ConfigItem(
            keyName = "eventLogger",
            name = "Event Logger",
            description = "Log game events and their ids to debug-logs/ in the working directory. Only works in " +
                    "developer mode.",
            section = debugSettings,
            position = 2
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
            position = 3
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
            position = 4
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
            position = 5
    )
    default boolean resetDetectors() {
        return false;
    }
}
