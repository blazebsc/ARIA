package org.blake7.aria.audio;

import com.mojang.logging.LogUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;

public class AriaAudioPlayer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static long device;
    private static long context;
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    public static void init() {
        if (initialized.getAndSet(true)) return;

        try {
            device = alcOpenDevice((ByteBuffer) null);
            if (device == 0L) {
                LOGGER.error("ARIA: Failed to open OpenAL device");
                return;
            }

            ALCCapabilities alcCaps = ALC.createCapabilities(device);
            context = alcCreateContext(device, (IntBuffer) null);
            if (context == 0L) {
                LOGGER.error("ARIA: Failed to create OpenAL context");
                return;
            }

            alcMakeContextCurrent(context);
            AL.createCapabilities(alcCaps);

            LOGGER.info("ARIA: OpenAL initialized successfully");
        } catch (Exception e) {
            LOGGER.error("ARIA: OpenAL initialization failed: {}", e.getMessage());
        }
    }

    public static int playPositional(byte[] pcmData, int sampleRate, float x, float y, float z, float volume, boolean muffled) {
        if (!initialized.get() || pcmData == null || pcmData.length == 0) return -1;

        int[] buffers = new int[1];
        alGenBuffers(buffers);
        int bufferId = buffers[0];

        ByteBuffer audioBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN);
        alBufferData(bufferId, AL_FORMAT_MONO16, audioBuffer, sampleRate);

        int[] sources = new int[1];
        alGenSources(sources);
        int sourceId = sources[0];

        alSourcei(sourceId, AL_BUFFER, bufferId);
        alSource3f(sourceId, AL_POSITION, x, y, z);
        alSourcef(sourceId, AL_GAIN, volume);
        alSourcei(sourceId, AL_SOURCE_RELATIVE, AL_FALSE);
        alSourcef(sourceId, AL_ROLLOFF_FACTOR, 2.0f);
        alSourcef(sourceId, AL_REFERENCE_DISTANCE, 8.0f);
        alSourcef(sourceId, AL_MAX_DISTANCE, 32.0f);

        if (muffled) {
            alSourcef(sourceId, AL_PITCH, 0.95f);
            alSourcef(sourceId, AL_GAIN, volume * 0.4f);
        }

        alSourcePlay(sourceId);

        int finalSourceId = sourceId;
        int finalBufferId = bufferId;
        new Thread(() -> {
            try {
                while (alGetSourcei(finalSourceId, AL_SOURCE_STATE) == AL_PLAYING) {
                    Thread.sleep(50);
                }
            } catch (InterruptedException ignored) {}
            finally {
                alSourceStop(finalSourceId);
                alDeleteSources(finalSourceId);
                alDeleteBuffers(finalBufferId);
            }
        }, "Aria-Audio-Cleanup").start();

        return sourceId;
    }

    public static void updateSourcePosition(int sourceId, float x, float y, float z) {
        if (!initialized.get() || sourceId < 0) return;
        alSource3f(sourceId, AL_POSITION, x, y, z);
    }

    public static void setSourceMuffled(int sourceId, boolean muffled, float baseVolume) {
        if (!initialized.get() || sourceId < 0) return;
        if (muffled) {
            alSourcef(sourceId, AL_GAIN, baseVolume * 0.4f);
            alSourcef(sourceId, AL_PITCH, 0.95f);
        } else {
            alSourcef(sourceId, AL_GAIN, baseVolume);
            alSourcef(sourceId, AL_PITCH, 1.0f);
        }
    }

    public static void shutdown() {
        if (!initialized.getAndSet(false)) return;
        if (context != 0L) {
            alcDestroyContext(context);
        }
        if (device != 0L) {
            alcCloseDevice(device);
        }
    }
}
