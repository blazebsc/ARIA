package org.blake7.aria;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class Config {

    public static class Common {
        public final ModConfigSpec.IntValue whisperPort;
        public final ModConfigSpec.IntValue ollamaPort;
        public final ModConfigSpec.IntValue f5TtsPort;
        public final ModConfigSpec.DoubleValue silenceThreshold;
        public final ModConfigSpec.IntValue silenceTimeoutMs;
        public final ModConfigSpec.IntValue stage1Days;
        public final ModConfigSpec.IntValue stage2Days;
        public final ModConfigSpec.IntValue stage3Days;
        public final ModConfigSpec.IntValue unpromptedIntervalStage1;
        public final ModConfigSpec.IntValue unpromptedIntervalStage2;
        public final ModConfigSpec.IntValue unpromptedIntervalStage3;
        public final ModConfigSpec.BooleanValue enableTts;
        public final ModConfigSpec.BooleanValue enableMicrophone;
        public final ModConfigSpec.BooleanValue enableHorrorEffects;

        public Common(ModConfigSpec.Builder builder) {
            builder.push("audio");
            whisperPort = builder.comment("Port for Faster Whisper STT server")
                    .defineInRange("whisperPort", 8181, 1, 65535);
            ollamaPort = builder.comment("Port for Ollama LLM server")
                    .defineInRange("ollamaPort", 11434, 1, 65535);
            f5TtsPort = builder.comment("Port for F5-TTS server")
                    .defineInRange("f5TtsPort", 8080, 1, 65535);
            silenceThreshold = builder.comment("Amplitude threshold for silence detection (0.0-1.0)")
                    .defineInRange("silenceThreshold", 0.01, 0.0, 1.0);
            silenceTimeoutMs = builder.comment("Milliseconds of silence before processing audio")
                    .defineInRange("silenceTimeoutMs", 1500, 100, 10000);
            enableTts = builder.comment("Enable F5-TTS voice synthesis")
                    .define("enableTts", true);
            enableMicrophone = builder.comment("Enable microphone input for voice chat")
                    .define("enableMicrophone", false);
            builder.pop();

            builder.push("stages");
            stage1Days = builder.comment("Number of days for Stage 1 (The Assistant)")
                    .defineInRange("stage1Days", 3, 1, 100);
            stage2Days = builder.comment("Number of days for Stage 2 (Too Close)")
                    .defineInRange("stage2Days", 4, 1, 100);
            stage3Days = builder.comment("Number of days for Stage 3 (Corrupted)")
                    .defineInRange("stage3Days", 8, 1, 100);
            builder.pop();

            builder.push("behavior");
            unpromptedIntervalStage1 = builder.comment("Ticks between unprompted dialogue in Stage 1")
                    .defineInRange("unpromptedIntervalStage1", 12000, 200, 120000);
            unpromptedIntervalStage2 = builder.comment("Ticks between unprompted dialogue in Stage 2")
                    .defineInRange("unpromptedIntervalStage2", 6000, 200, 120000);
            unpromptedIntervalStage3 = builder.comment("Ticks between unprompted dialogue in Stage 3")
                    .defineInRange("unpromptedIntervalStage3", 3000, 200, 120000);
            enableHorrorEffects = builder.comment("Enable Stage 3 horror visual effects")
                    .define("enableHorrorEffects", true);
            builder.pop();
        }
    }

    public static class Client {
        public final ModConfigSpec.BooleanValue showSubtitles;
        public final ModConfigSpec.DoubleValue volumeMultiplier;
        public final ModConfigSpec.DoubleValue ttsSpeed;
        public final ModConfigSpec.BooleanValue enableTextChat;
        public final ModConfigSpec.IntValue textChatColor;

        public Client(ModConfigSpec.Builder builder) {
            builder.push("audio");
            showSubtitles = builder.comment("Show subtitles for Aria's speech")
                    .define("showSubtitles", true);
            volumeMultiplier = builder.comment("Volume multiplier for Aria's voice (0.0-2.0)")
                    .defineInRange("volumeMultiplier", 1.0, 0.0, 2.0);
            ttsSpeed = builder.comment("Speech speed multiplier for Aria's voice (0.5-3.0)")
                    .defineInRange("ttsSpeed", 2.0, 0.5, 3.0);
            builder.pop();

            builder.push("textChat");
            enableTextChat = builder.comment("Enable text chat responses from Aria (chat near Aria to talk)")
                    .define("enableTextChat", true);
            textChatColor = builder.comment("Text color for Aria's chat messages (hex, e.g. 0xFFAA00)")
                    .defineInRange("textChatColor", 0xFFAA00, 0x000000, 0xFFFFFF);
            builder.pop();
        }
    }

    public static final ModConfigSpec COMMON_SPEC;
    public static final Common COMMON;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        Pair<Common, ModConfigSpec> commonPair = new ModConfigSpec.Builder().configure(Common::new);
        COMMON = commonPair.getLeft();
        COMMON_SPEC = commonPair.getRight();

        Pair<Client, ModConfigSpec> clientPair = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = clientPair.getLeft();
        CLIENT_SPEC = clientPair.getRight();
    }
}
