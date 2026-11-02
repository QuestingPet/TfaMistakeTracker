package com.tfamistaketracker;

import lombok.Getter;
import lombok.NonNull;
import net.runelite.client.util.ImageUtil;

import java.awt.image.BufferedImage;
import java.util.function.Function;
import java.util.Arrays;

public enum TfaMistake {
    // Deaths
    DEATH("Death", TfaMistakeTrackerConfig::deathMessage, defaultDeathMessage(), "death.png"),

    // Archive Guardian
    ARCHIVE_GUARDIAN_SLAM("Archive Guardian Slam", TfaMistakeTrackerConfig::archiveGuardianSlamMessage,
            "I'm getting slammed!", ""),
    ARCHIVE_GUARDIAN_MELEE("Archive Guardian Melee", TfaMistakeTrackerConfig::archiveGuardianMeleeMessage,
            "Ow that hurt!", ""),
    ARCHIVE_GUARDIAN_RANGE("Archive Guardian Range", TfaMistakeTrackerConfig::archiveGuardianRangeMessage,
            "I got rocked!", ""),
    ARCHIVE_GUARDIAN_MUSHROOM("Archive Guardian Mushroom", TfaMistakeTrackerConfig::archiveGuardianMushroomMessage,
            "*Cough*", ""),
    ARCHIVE_GUARDIAN_VINE("Archive Guardian Vine", TfaMistakeTrackerConfig::archiveGuardianVineMessage,
            "Ahh! Tentacles!", ""),

    // Bork
    BORK_STOMP("Bork Stomp", TfaMistakeTrackerConfig::borkStompMessage, "He's stomping!", ""),
    BORK_MELEE("Bork Melee", TfaMistakeTrackerConfig::borkMeleeMessage, "He's hitting me!", ""),
    BORK_SMORKS_AA("Bork Smorks Auto Attack", TfaMistakeTrackerConfig::borkSmorksAaMessage,
            "Whoops wrong prayer!", ""),
    BORK_FIRE("Bork Fire", TfaMistakeTrackerConfig::borkFireMessage, "It burns!", ""),

    // Dreadnoth
    DREADNOTH_SLAM("Dreadnoth Slam", TfaMistakeTrackerConfig::dreadnothSlamMessage, "It's an earthquake!", ""),
    DREADNOTH_RANGE("Dreadnoth Range", TfaMistakeTrackerConfig::dreadnothRangeMessage, "I'm hit!", ""),
    DREADNOTH_BLOOD_POOL("Dreadnoth Blood Pool", TfaMistakeTrackerConfig::dreadnothBloodPoolMessage, "I'm drowning in blood!", ""),
    DREADNOTH_BLOOD_BALL("Dreadnoth Blood Ball", TfaMistakeTrackerConfig::dreadnothBloodBallMessage,
            "Who put that there?", ""),
    DREADNOTH_LANDED("Dreadnoth Landed", TfaMistakeTrackerConfig::dreadnothLandedMessage, "Bonk!", ""),
    DREADNOTH_JELLY("Dreadnoth Jelly", TfaMistakeTrackerConfig::dreadnothJellyMessage, "I'm smited!", ""),
    DREADNOTH_INSIDE("Dreadnoth Inside", TfaMistakeTrackerConfig::dreadnothInsideMessage, "What's inside?", ""),
    ;

    private static final String FALLBACK_IMAGE_PATH = "death.png";

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

    TfaMistake(@NonNull String mistakeName, @NonNull Function<TfaMistakeTrackerConfig, String> chatMessageFunc, @NonNull String defaultMessage,
            @NonNull String mistakeImagePath) {
        this.mistakeName = mistakeName;
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
