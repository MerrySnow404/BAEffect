package com.sighs.baeffect;

import com.mojang.blaze3d.audio.OggAudioStream;
import org.lwjgl.openal.AL10;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

final class PhiraSound {
    private static final Map<String, Integer> BUFFERS = new HashMap<>();
    private static final Map<String, Pcm> PCM_CACHE = new HashMap<>();
    private static final List<Integer> SOURCES = new ArrayList<>();
    private static final java.util.Set<String> FAILED = new java.util.HashSet<>();
    private static final int MAX_SOURCES = 8;
    private static final int MAX_CACHED_PCM_BYTES = 8 * 1024 * 1024;
    private static int cachedBytes;

    private static final String[] CANDIDATES = {"click.ogg", "drag.ogg", "flick.ogg"};

    private static long lastContext;

    private record Pcm(byte[] data, int sampleRate, boolean stereo, boolean eightBit) {
    }

    private PhiraSound() {
    }

    static void playHit(PhiraRespack pack) {
        try {
            if (pack == null || !Baeffect.flag("phiraSound")) return;
            if (!ensureContext()) return;

            List<String> candidates = new ArrayList<>(3);
            for (String name : CANDIDATES) {
                if (!pack.sounds.containsKey(name)) continue;
                if (FAILED.contains(pack.id + "/" + name)) continue;
                candidates.add(name);
            }
            if (candidates.isEmpty()) return;

            String chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            int buffer = bufferFor(pack, chosen);
            if (buffer <= 0) {
                for (String other : candidates) {
                    if (other.equals(chosen)) continue;
                    int fallback = bufferFor(pack, other);
                    if (fallback > 0) {
                        play(fallback);
                        return;
                    }
                }
                return;
            }
            play(buffer);
        } catch (Throwable throwable) {
            Baeffect.LOGGER.warn("资源包音效播放异常：{}", String.valueOf(throwable));
        }
    }

    private static boolean ensureContext() {
        long context = org.lwjgl.openal.ALC10.alcGetCurrentContext();
        if (context == 0L) return false;
        if (context == lastContext) return true;

        SOURCES.clear();
        BUFFERS.clear();
        lastContext = context;
        return true;
    }

    private static int bufferFor(PhiraRespack pack, String name) {
        String key = pack.id + "/" + name;
        Integer cached = BUFFERS.get(key);
        if (cached != null) return cached;

        Pcm pcm = pcmFor(pack, name, key);
        if (pcm == null) return 0;

        ByteBuffer buffer = ByteBuffer.allocateDirect(pcm.data().length);
        try {
            buffer.put(pcm.data()).flip();
            int handle = AL10.alGenBuffers();
            if (handle == 0) return 0;
            int format = pcm.eightBit()
                    ? (pcm.stereo() ? AL10.AL_FORMAT_STEREO8 : AL10.AL_FORMAT_MONO8)
                    : (pcm.stereo() ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_MONO16);
            AL10.alBufferData(handle, format, buffer, pcm.sampleRate());
            BUFFERS.put(key, handle);
            return handle;
        } catch (Throwable throwable) {
            FAILED.add(key);
            Baeffect.LOGGER.warn("资源包音效缓存失败，跳过该文件：{}：{}", key, throwable);
            return 0;
        }
    }

    private static Pcm pcmFor(PhiraRespack pack, String name, String key) {
        Pcm cached = PCM_CACHE.get(key);
        if (cached != null) return cached;

        byte[] ogg = pack.sounds.get(name);
        if (ogg == null) return null;

        try (OggAudioStream stream = new OggAudioStream(new ByteArrayInputStream(ogg))) {
            AudioFormat format = stream.getFormat();
            ByteBuffer raw = stream.readAll();
            if (raw == null || raw.remaining() == 0) return null;
            raw.rewind();

            byte[] data = new byte[raw.remaining()];
            raw.get(data);

            Pcm pcm = new Pcm(data, (int) format.getSampleRate(),
                    format.getChannels() >= 2, format.getSampleSizeInBits() == 8);

            if (cachedBytes + data.length <= MAX_CACHED_PCM_BYTES) {
                PCM_CACHE.put(key, pcm);
                cachedBytes += data.length;
            }
            return pcm;
        } catch (Throwable throwable) {
            FAILED.add(key);
            Baeffect.LOGGER.warn("资源包音效解码失败，跳过：{}：{}", key, throwable);
            return null;
        }
    }

    private static void play(int buffer) {
        Integer source = acquire();
        if (source == null) return;
        AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
        AL10.alSourcef(source, AL10.AL_GAIN, 0.65f);
        AL10.alSourcef(source, AL10.AL_PITCH, 1.0f);
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSource3f(source, AL10.AL_POSITION, 0.0f, 0.0f, 0.0f);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0.0f);
        AL10.alSourcePlay(source);

        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR && !playErrorLogged) {
            playErrorLogged = true;
            Baeffect.LOGGER.warn("资源包音效播放失败（AL 错误码 {}），请检查音频设备", error);
        }
    }

    private static boolean playErrorLogged;

    private static Integer acquire() {
        for (int source : SOURCES) {
            if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) return source;
        }
        if (SOURCES.size() >= MAX_SOURCES) return null;
        int source = AL10.alGenSources();
        if (source == 0) return null;
        SOURCES.add(source);
        return source;
    }

    static void reset() {
        try {
            for (int source : SOURCES) {
                AL10.alDeleteSources(source);
            }
            for (int buffer : BUFFERS.values()) {
                AL10.alDeleteBuffers(buffer);
            }
        } catch (Throwable ignored) {
        }
        SOURCES.clear();
        BUFFERS.clear();
        PCM_CACHE.clear();
        FAILED.clear();
        cachedBytes = 0;
    }
}
