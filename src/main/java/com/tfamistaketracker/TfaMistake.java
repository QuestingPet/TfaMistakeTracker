package com.tfamistaketracker;

import lombok.Getter;
import lombok.NonNull;
import net.runelite.client.util.ImageUtil;

import java.awt.image.BufferedImage;
import java.util.function.Function;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public enum TfaMistake {
    // Deaths
    DEATH("Death", (config) -> "", "", "death.png", false), // Chat message handled in deaths below
    DEATH_ARCHIVE_GUARDIAN("Archive Guardian Death", TfaMistakeTrackerConfig::deathMessage, defaultDeathMessage(),
            "death-ag.png", false),
    DEATH_EZEKIEL("Ezekiel Death", TfaMistakeTrackerConfig::deathMessage, defaultDeathMessage(), "death.png", false),
    DEATH_BORK("Bork Death", TfaMistakeTrackerConfig::deathMessage, defaultDeathMessage(), "death-bork.png", false),
    DEATH_MOIA("Moia Death", TfaMistakeTrackerConfig::deathMessage, defaultDeathMessage(), "death.png", false),
    DEATH_DREADNOTH("Dreadnoth Death", TfaMistakeTrackerConfig::deathMessage, defaultDeathMessage(),
            "death-dreadnoth.png", false),
    DEATH_DAGONHAI("Dagon'hai Death", TfaMistakeTrackerConfig::deathMessage, defaultDeathMessage(), "death.png", false),
    DEATH_XARTH("Xarth Death", TfaMistakeTrackerConfig::deathMessage, defaultDeathMessage(), "death-xarth.png", false),
    DEATH_OCELLUS("Ocellus Death", TfaMistakeTrackerConfig::deathMessage, defaultDeathMessage(),
            "death-ocellus.png", false),

    // Archive Guardian
    ARCHIVE_GUARDIAN_FALL("Archive Guardian Fall", TfaMistakeTrackerConfig::archiveGuardianFallMessage,
            "I'm getting slammed!", "ag-fall.png", false),
    ARCHIVE_GUARDIAN_MELEE("Archive Guardian Melee", TfaMistakeTrackerConfig::archiveGuardianMeleeMessage,
            "Ow that hurt!", "ag-melee.png", true),
    ARCHIVE_GUARDIAN_RANGE("Archive Guardian Range", TfaMistakeTrackerConfig::archiveGuardianRangeMessage,
            "I got rocked!", "ag-range-attack.png", true),
    ARCHIVE_GUARDIAN_MUSHROOM("Archive Guardian Mushroom", TfaMistakeTrackerConfig::archiveGuardianMushroomMessage,
            "*Cough*", "ag-mushroom.png", false),
    ARCHIVE_GUARDIAN_VINE("Archive Guardian Vine", TfaMistakeTrackerConfig::archiveGuardianVineMessage,
            "Ahh! Tentacles!", "ag-vine.png", false),

    // Bork
    BORK_STOMP("Bork Stomp", TfaMistakeTrackerConfig::borkStompMessage, "He's stomping!", "bork-stomp.png", true),
    BORK_MELEE("Bork Melee", TfaMistakeTrackerConfig::borkMeleeMessage, "He's hitting me!", "bork-melee.png", true),
    BORK_SMORKS_AA("Bork Smorks Auto Attack", TfaMistakeTrackerConfig::borkSmorksAaMessage,
            "Whoops wrong prayer!", "bork-smorks-aa.png", true),
    BORK_FIRE("Bork Fire", TfaMistakeTrackerConfig::borkFireMessage, "It burns!", "bork-smorks-fire-tile.png", false),
    BORK_FIREBALL("Bork Fireball", TfaMistakeTrackerConfig::borkFireballMessage, "I got cannonballed!",
            "bork-smorks-fireball.png", false),
    BORK_CANNONBALL("Bork Cannonball", TfaMistakeTrackerConfig::borkCannonballMessage, "I came in like a wrecking ball!",
            "bork-smorks-fireball.png", false),

    // Moia
    MOIA_BINDER_MELEE("Moia Binder Melee", TfaMistakeTrackerConfig::moiaBinderMeleeMessage, "Don't touch me!",
            "prot-melee.png", true),
    MOIA_RANGE("Moia Range", TfaMistakeTrackerConfig::moiaRangeMessage, "I forgot to switch back!",
            "prot-range.png", true),
    MOIA_MAGIC("Moia Magic", TfaMistakeTrackerConfig::moiaMagicMessage, "I've been backstabbed!",
            "prot-magic.png", true),

    // Dreadnoth
    DREADNOTH_SLAM("Dreadnoth Slam", TfaMistakeTrackerConfig::dreadnothSlamMessage, "It's an earthquake!",
            "dreadnoth-slam.png", true),
    DREADNOTH_RANGE("Dreadnoth Range", TfaMistakeTrackerConfig::dreadnothRangeMessage, "I'm hit!",
            "dreadnoth-range.png", true),
    DREADNOTH_BLOOD_POOL("Dreadnoth Blood Pool", TfaMistakeTrackerConfig::dreadnothBloodPoolMessage, "I'm drowning in blood!",
            "dreadnoth-blood.png", false),
    DREADNOTH_BLOOD_BALL("Dreadnoth Blood Ball", TfaMistakeTrackerConfig::dreadnothBloodBallMessage,
            "Who put that there?", "dreadnoth-blood-ball.png", false),
    DREADNOTH_LANDED("Dreadnoth Landed", TfaMistakeTrackerConfig::dreadnothLandedMessage, "Bonk!",
            "dreadnoth-landed.png", false),
    DREADNOTH_JELLY("Dreadnoth Jelly", TfaMistakeTrackerConfig::dreadnothJellyMessage, "I'm smited!",
            "dreadnoth-jelly.png", false),
    DREADNOTH_INSIDE("Dreadnoth Inside", TfaMistakeTrackerConfig::dreadnothInsideMessage, "What's inside?",
            "dreadnoth-inside.png", false),

    // Xarth
    XARTH_TETHER("Xarth Tether", TfaMistakeTrackerConfig::xarthTetherMessage, "I was checkmated!",
            "xarth-tether.png", false),
    XARTH_EXPLOSION("Xarth Explosion", TfaMistakeTrackerConfig::xarthExplosionMessage, "I'm exploding!",
            "xarth-explosion.png", true),
    XARTH_FIRE("Xarth Fire", TfaMistakeTrackerConfig::xarthFireMessage, "I'm burning!", "xarth-fire.png", false),
    XARTH_MELEE("Xarth Melee", TfaMistakeTrackerConfig::xarthMeleeMessage, "The Hash-Slinging Slasher!",
            "xarth-melee.png", true),
    XARTH_CORPSE_HEAL("Xarth Corpse Heal", TfaMistakeTrackerConfig::xarthCorpseHealMessage, "We're healing him!",
            "xarth-heal.png", false),

    // Ocellus
    OCELLUS_P1_AUTO_ATTACK("Ocellus P1 Auto Attack", TfaMistakeTrackerConfig::ocellusP1AutoAttackMessage,
            "Whoops wrong prayer!", "ocellus-p1-aa.png", true),
    OCELLUS_P1_SLAM("Ocellus P1 Slam", TfaMistakeTrackerConfig::ocellusP1SlamMessage, "I moved!",
            "ocellus-p1-slam.png", true),
    OCELLUS_P2_PORTAL("Ocellus P2 Portal", TfaMistakeTrackerConfig::ocellusP2PortalMessage, "I'm too slow!",
            "ocellus-p2-portals.png", false),
    OCELLUS_P2_PURPLE_TILE("Ocellus P2 Purple Tile", TfaMistakeTrackerConfig::ocellusP2PurpleTileMessage,
            "I stood on purple!", "ocellus-p2-purple.png", false),
    OCELLUS_P3_VOID_GRIEF("Ocellus P3 Void Grief", TfaMistakeTrackerConfig::ocellusP3VoidGriefMessage,
            "Who griefed this tile?", "ocellus-p3-void-grief.png", false),
    OCELLUS_P3_AOE("Ocellus P3 AOE", TfaMistakeTrackerConfig::ocellusP3AoeMessage, "I'm getting sweeped!",
            "ocellus-p3-aoe.png", false),
    OCELLUS_P3_INSOLENCE("Ocellus P3 Insolence", TfaMistakeTrackerConfig::ocellusP3InsolenceMessage, "Insulin!",
            "ocellus-p3-insolence.png", false),
    OCELLUS_P3_MELEE_PARRY("Ocellus P3 Melee Parry", TfaMistakeTrackerConfig::ocellusP3MeleeParryMessage, "I missed the parry!",
            "ocellus-p3-melee-parry.png", true),
    OCELLUS_P3_TANK_MAGE("Ocellus P3 Tank Mage", TfaMistakeTrackerConfig::ocellusP3TankMageMessage,
            "I'm PKing my team!", "ocellus-p3-tank-mage.png", false),
    OCELLUS_P3_VOID_FLARE_DETONATION("Ocellus P3 Void Flare Detonation", TfaMistakeTrackerConfig::ocellusP3VoidFlareDetonationMessage,
            "We failed to destroy it!", "ocellus-p3-void-flare-detonation.png", false),
    OCELLUS_P3_UNTETHER_EXPLOSION("Ocellus P3 Untether Explosion",
            TfaMistakeTrackerConfig::ocellusP3UntetherExplosionMessage, "Space is bad!",
            "ocellus-p3-untether-explosion.png", false),
    OCELLUS_P3_UNTETHER_MAGE("Ocellus P3 Untether Mage", TfaMistakeTrackerConfig::ocellusP3UntetherMageMessage,
            "I'm not praying!", "ocellus-p4-untether-mage.png", true),
    OCELLUS_P3_FALL("Ocellus P3 Fall", TfaMistakeTrackerConfig::ocellusP3FallMessage, "I'm falling into space!",
            "ocellus-p3-fall.png", false),
    OCELLUS_P4_VOID_SPACE("Ocellus P4 Void Space", TfaMistakeTrackerConfig::ocellusP4VoidSpaceMessage, "Space is bad!",
            "ocellus-p4-void-space.png", false),
    OCELLUS_P4_VOID_EXPLOSION("Ocellus P4 Void Explosion", TfaMistakeTrackerConfig::ocellusP4VoidExplosionMessage,
            "I'm failing my 2-tick!", "ocellus-p4-void-explosion.png", false),
    OCELLUS_P4_PRAYER_ATTACK("Ocellus P4 Prayer Attack", TfaMistakeTrackerConfig::ocellusP4PrayerAttackMessage,
            "Don't look at me!", "ocellus-p4-prayer-attack.png", true),
    ;

    private static final String FALLBACK_IMAGE_PATH = "death.png";

    private static final Set<TfaMistake> ROOM_DEATHS = EnumSet.of(DEATH_ARCHIVE_GUARDIAN, DEATH_EZEKIEL, DEATH_BORK,
            DEATH_MOIA, DEATH_DREADNOTH, DEATH_DAGONHAI, DEATH_XARTH, DEATH_OCELLUS);

    private static final int MAX_STACKING_CHAT_MESSAGE_LENGTH = 10;
    public static final int MAX_MESSAGE_LENGTH = 40;

    @Getter
    @NonNull
    private final String mistakeName;

    @Getter
    @NonNull
    private final String defaultMessage;

    @NonNull
    private final Function<TfaMistakeTrackerConfig, String> chatMessageFunc;

    @Getter
    @NonNull
    private final BufferedImage mistakeImage;

    // A mistake that can be venged on purpose, so it is dropped when the raider pops vengeance
    @Getter
    private final boolean vengeableMistake;

    TfaMistake(@NonNull String mistakeName, @NonNull Function<TfaMistakeTrackerConfig, String> chatMessageFunc, @NonNull String defaultMessage,
            @NonNull String mistakeImagePath, boolean vengeableMistake) {
        this.mistakeName = mistakeName;
        this.vengeableMistake = vengeableMistake;
        this.chatMessageFunc = chatMessageFunc;
        this.defaultMessage = defaultMessage;

        final String imagePath;
        if (mistakeImagePath.isEmpty()) {
            imagePath = FALLBACK_IMAGE_PATH;
        } else {
            imagePath = mistakeImagePath;
        }
        this.mistakeImage = ImageUtil.loadImageResource(getClass(), imagePath);
    }

    public String getChatMessage(TfaMistakeTrackerConfig config) {
        return chatMessageFunc.apply(config);
    }

    public static boolean isRoomDeath(TfaMistake mistake) {
        return ROOM_DEATHS.contains(mistake);
    }

    public static String defaultDeathMessage() {
        return "I'm planking!";
    }

    /**
     * Retrieve the chat message for the given mistake, considering special cases given the config
     * settings about whether to stack question marks and the current mistake count of either
     * this specific mistake in the raid for the current raider, *or* the total current raid mistake
     * count for the current raider.
     *
     * @param config       The configuration object to retrieve chat message from
     * @param mistake      The mistake
     * @param mistakeCount The current mistake count of this mistake *or* the current raid mistake count for the
     *                     raider in this raid
     *
     * @return The mistake chat message to use for the raider
     */
    public static String getChatMessageForMistakeCount(TfaMistakeTrackerConfig config, TfaMistake mistake,
            int mistakeCount) {
        String mistakeMessage = mistake.getChatMessage(config);
        // Special case a few mistake chat messages based on config
        if (config.mistakeMessageStacking() != StackingBehavior.NONE && mistakeMessage.equals("?")) {
            return getStackingChatMessage(mistakeMessage, mistakeCount);
        }
        if (mistakeMessage.contains("|")) {
            return getAlternatingChatMessage(mistakeMessage, mistake.getDefaultMessage(), mistakeCount);
        }

        return mistakeMessage;
    }

    private static String getStackingChatMessage(String message, int mistakeCount) {
        // Keep adding ?s to the text for every subsequent mistake in this raid. A bit hacky but it's funny.
        StringBuilder sb = new StringBuilder(message);
        for (int i = 0; i < Math.min(mistakeCount, MAX_STACKING_CHAT_MESSAGE_LENGTH); i++) {
            sb.append(message);
        }
        return sb.toString();
    }

    private static String getAlternatingChatMessage(String message, String defaultMessage, int mistakeCount) {
        String[] messageChoices = Arrays.stream(message.split("\\|")).filter(msg -> !msg.isEmpty())
                .toArray(String[]::new);
        if (messageChoices.length == 0) {
            return defaultMessage;
        }
        return messageChoices[mistakeCount % messageChoices.length];
    }
}
