package org.blake7.aria.chat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.blake7.aria.Config;
import org.blake7.aria.audio.AriaAudioPlayer;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.concurrent.*;

public class AriaTtsManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    // HTTP/1.1 explicitly — Uvicorn rejects HTTP/2 upgrade requests which causes 422s
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private static final ExecutorService TTS_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Aria-TTS");
        t.setDaemon(true);
        return t;
    });

    private static volatile int currentSourceId = -1;

    public interface TtsCallback {
        void onPlaybackStarted(int sourceId);
        void onPlaybackComplete();
    }

    private static String getTtsUrl() {
        return "http://127.0.0.1:" + Config.COMMON.f5TtsPort.get() + "/v1/audio/speech";
    }

    public static void speak(String text, float x, float y, float z,
                              float volume, boolean muffled, TtsCallback callback) {
        TTS_EXECUTOR.submit(() -> {
            try {
                // 1. Fetch WAV from TTS server
                byte[] wavData = synthesizeSpeech(text);
                if (wavData == null) {
                    LOGGER.warn("ARIA TTS: no audio returned for text ({} chars)", text.length());
                    if (callback != null) callback.onPlaybackComplete();
                    return;
                }

                // 2. Parse WAV properly — finds the data chunk rather than assuming offset 44
                WavData wav = parseWav(wavData);
                if (wav == null) {
                    if (callback != null) callback.onPlaybackComplete();
                    return;
                }

                // 3. Start positional playback on the render thread via Minecraft's OpenAL context
                int sourceId = AriaAudioPlayer.playPositional(
                        wav.pcm(), wav.sampleRate(), wav.channels(), x, y, z, volume, muffled);
                if (sourceId < 0) {
                    LOGGER.warn("ARIA TTS: playback failed to start");
                    if (callback != null) callback.onPlaybackComplete();
                    return;
                }
                currentSourceId = sourceId;

                if (callback != null) callback.onPlaybackStarted(sourceId);

                // 4. Wait for audio to actually finish before firing onPlaybackComplete.
                //    Duration = PCM bytes / (sampleRate × channels × 2 bytes/sample)
                //    +400ms safety buffer so the last word isn't cut off.
                float durationSec = wav.pcm().length / (float)(wav.sampleRate() * wav.channels() * 2);
                durationSec /= Math.max(0.5f, Config.CLIENT.ttsSpeed.get().floatValue());
                long waitMs = (long)(durationSec * 1000L) + 400L;
                Thread.sleep(waitMs);

            } catch (InterruptedException ignored) {
                // Interrupted mid-wait — still clean up below
            } catch (Exception e) {
                LOGGER.error("ARIA TTS: speak error — {}", e.getMessage());
            } finally {
                // Always fires — but now only AFTER the wait, so timing is correct
                if (callback != null) callback.onPlaybackComplete();
            }
        });
    }

    // ── HTTP ─────────────────────────────────────────────────────────────────

    private static byte[] synthesizeSpeech(String text) {
        // Normalize all-caps "ARIA" → "Aria" so TTS says the word
        // instead of spelling out A-R-I-A
        text = text.replaceAll("\\bARIA\\b", "Aria");

        try {
            JsonObject body = new JsonObject();
            body.addProperty("model",  "tts-1");
            body.addProperty("input",  text);
            body.addProperty("voice",  "aria");
            body.addProperty("speed",  Config.CLIENT.ttsSpeed.get());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getTtsUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .timeout(Duration.ofSeconds(180))
                    .build();

            HttpResponse<byte[]> response = HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                LOGGER.debug("ARIA TTS: received {} bytes", response.body().length);
                return response.body();
            }
            LOGGER.warn("ARIA TTS: server returned {} — {}",
                    response.statusCode(), new String(response.body()));

        } catch (Exception e) {
            LOGGER.error("ARIA TTS: request failed — {}", e.getMessage());
        }
        return null;
    }

    // ── WAV parsing ──────────────────────────────────────────────────────────

    private record WavData(byte[] pcm, int sampleRate, int channels) {}

    /**
     * Walks the RIFF chunk tree to find the "fmt " and "data" chunks.
     * This is correct even when the encoder writes LIST/INFO or other chunks
     * before the audio data (soundfile does this), which breaks the naive
     * "skip 44 bytes" approach.
     */
    private static WavData parseWav(byte[] wav) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);

            // RIFF header
            if (buf.getInt() != 0x46464952) {  // "RIFF"
                LOGGER.warn("ARIA TTS: not a RIFF file");
                return null;
            }
            buf.getInt(); // file size (skip)
            if (buf.getInt() != 0x45564157) {  // "WAVE"
                LOGGER.warn("ARIA TTS: not a WAVE file");
                return null;
            }

            int sampleRate = 24000;
            int channels   = 1;
            byte[] pcm     = null;

            while (buf.remaining() >= 8) {
                int chunkId   = buf.getInt();
                int chunkSize = buf.getInt();

                if (chunkId == 0x20746D66) {       // "fmt "
                    buf.getShort();                // audio format (1 = PCM)
                    channels   = Short.toUnsignedInt(buf.getShort());
                    sampleRate = buf.getInt();
                    buf.getInt();                  // byte rate
                    buf.getShort();                // block align
                    buf.getShort();                // bits per sample
                    // skip any extra fmt extension bytes
                    int extra = chunkSize - 16;
                    if (extra > 0) buf.position(buf.position() + extra);

                } else if (chunkId == 0x61746164) { // "data"
                    pcm = new byte[chunkSize];
                    buf.get(pcm);
                    break; // found everything we need

                } else {
                    // Unknown chunk (LIST, INFO, etc.) — skip, keeping word alignment
                    int skip = chunkSize + (chunkSize & 1);
                    if (skip > buf.remaining()) break;
                    buf.position(buf.position() + skip);
                }
            }

            if (pcm == null) {
                LOGGER.warn("ARIA TTS: no data chunk found in WAV");
                return null;
            }

            LOGGER.debug("ARIA TTS: parsed WAV — {}Hz {}ch {} bytes PCM",
                    sampleRate, channels, pcm.length);
            return new WavData(pcm, sampleRate, channels);

        } catch (Exception e) {
            LOGGER.error("ARIA TTS: WAV parse error — {}", e.getMessage());
            return null;
        }
    }

    // ── Misc ─────────────────────────────────────────────────────────────────

    public static int getCurrentSourceId()  { return currentSourceId; }
    public static void stopCurrent()        { currentSourceId = -1; }
}