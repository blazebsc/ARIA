package org.blake7.aria.chat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.blake7.aria.audio.AriaAudioPlayer;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.*;

public class AriaTtsManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    private static final String F5_TTS_URL = "http://localhost:8080/v1/audio/speech";
    private static final String TTS_MODEL = "tts-1";

    private static final ExecutorService ttsExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Aria-TTS");
        t.setDaemon(true);
        return t;
    });

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static volatile int currentSourceId = -1;

    public interface TtsCallback {
        void onPlaybackStarted(int sourceId);
        void onPlaybackComplete();
    }

    public static void speak(String text, float x, float y, float z, float volume, boolean muffled, TtsCallback callback) {
        ttsExecutor.submit(() -> {
            try {
                byte[] wavData = synthesizeSpeech(text);
                if (wavData != null) {
                    byte[] pcmData = extractPcmFromWav(wavData);
                    if (pcmData != null) {
                        AriaAudioPlayer.init();
                        int sourceId = AriaAudioPlayer.playPositional(pcmData, 24000, x, y, z, volume, muffled);
                        currentSourceId = sourceId;
                        callback.onPlaybackStarted(sourceId);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("ARIA TTS error: {}", e.getMessage());
            } finally {
                callback.onPlaybackComplete();
            }
        });
    }

    private static byte[] synthesizeSpeech(String text) {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", TTS_MODEL);
            requestBody.addProperty("input", text);
            requestBody.addProperty("voice", "aria");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(F5_TTS_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                LOGGER.warn("ARIA TTS returned status {}", response.statusCode());
            }
        } catch (Exception e) {
            LOGGER.error("ARIA TTS request error: {}", e.getMessage());
        }
        return null;
    }

    private static byte[] extractPcmFromWav(byte[] wavData) {
        try {
            if (wavData.length < 44) return null;
            int dataSize = (wavData[40] & 0xFF) | ((wavData[41] & 0xFF) << 8)
                    | ((wavData[42] & 0xFF) << 16) | ((wavData[43] & 0xFF) << 24);
            byte[] pcmData = new byte[dataSize];
            System.arraycopy(wavData, 44, pcmData, 0, Math.min(dataSize, wavData.length - 44));
            return pcmData;
        } catch (Exception e) {
            LOGGER.error("WAV extraction error: {}", e.getMessage());
            return null;
        }
    }

    public static int getCurrentSourceId() { return currentSourceId; }
    public static void stopCurrent() { currentSourceId = -1; }
}
