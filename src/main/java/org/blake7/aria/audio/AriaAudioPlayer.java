package org.blake7.aria.audio;

import com.mojang.logging.LogUtils;
import org.blake7.aria.Config;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plays TTS PCM through Java's audio stack (PulseAudio/ALSA), avoiding OpenAL conflicts.
 */
public class AriaAudioPlayer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicInteger PLAYBACK_ID = new AtomicInteger(1);

    public static int playPositional(byte[] pcmData, int sampleRate, int channels,
                                     float x, float y, float z, float volume, boolean muffled) {
        if (pcmData == null || pcmData.length == 0 || sampleRate <= 0 || channels <= 0) {
            return -1;
        }

        byte[] pcm = channels == 1 ? pcmData : downmixToMono(pcmData, channels);
        float effectiveVolume = volume * Config.CLIENT.volumeMultiplier.get().floatValue();
        if (muffled) {
            effectiveVolume *= 0.4f;
        }

        int playbackId = PLAYBACK_ID.getAndIncrement();
        float finalVolume = effectiveVolume;
        Thread playbackThread = new Thread(
                () -> playPcm(pcm, sampleRate, finalVolume, playbackId),
                "Aria-Audio-" + playbackId);
        playbackThread.setDaemon(true);
        playbackThread.start();
        return playbackId;
    }

    public static void updateSourcePosition(int sourceId, float x, float y, float z) {
    }

    public static void setSourceMuffled(int sourceId, boolean muffled, float baseVolume) {
    }

    private static void playPcm(byte[] pcm, int sampleRate, float volume, int playbackId) {
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                LOGGER.error("ARIA: no audio output line available for TTS");
                return;
            }

            try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                line.open(format);
                applyVolume(line, volume);
                line.start();
                LOGGER.info("ARIA: playing TTS audio ({} bytes, {}Hz, id={})", pcm.length, sampleRate, playbackId);
                line.write(pcm, 0, pcm.length);
                line.drain();
            }
        } catch (Exception e) {
            LOGGER.error("ARIA: TTS playback failed (id={}): {}", playbackId, e.getMessage());
        }
    }

    private static void applyVolume(SourceDataLine line, float volume) {
        if (!line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
        float clamped = Math.max(0.0001f, Math.min(volume, 2.0f));
        float dB = (float) (20.0 * Math.log10(clamped));
        gain.setValue(Math.min(gain.getMaximum(), Math.max(gain.getMinimum(), dB)));
    }

    static byte[] downmixToMono(byte[] pcmData, int channels) {
        int frameCount = pcmData.length / (2 * channels);
        byte[] mono = new byte[frameCount * 2];
        ByteBuffer in = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer out = ByteBuffer.wrap(mono).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < frameCount; i++) {
            int sum = 0;
            for (int c = 0; c < channels; c++) {
                sum += in.getShort();
            }
            out.putShort((short) (sum / channels));
        }
        return mono;
    }
}
