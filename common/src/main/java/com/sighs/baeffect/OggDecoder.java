package com.sighs.baeffect;

import com.mojang.blaze3d.audio.OggAudioStream;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * OGG/Vorbis decoder used for Phira resource-pack sounds.
 *
 * <p>This implementation targets Minecraft 1.20.1 through 1.21.1. Minecraft
 * 26.1 replaced {@code OggAudioStream} with {@code JOrbisAudioStream}; those
 * targets exclude this file and provide their own implementation.</p>
 */
public final class OggDecoder {
    public record Decoded(byte[] data, int sampleRate, boolean stereo, boolean eightBit) {
    }

    private OggDecoder() {
    }

    public static Decoded decode(byte[] ogg) throws IOException {
        try (OggAudioStream stream = new OggAudioStream(new ByteArrayInputStream(ogg))) {
            AudioFormat format = stream.getFormat();
            ByteBuffer raw = stream.readAll();
            if (raw == null || raw.remaining() == 0) return null;
            raw.rewind();

            byte[] data = new byte[raw.remaining()];
            raw.get(data);
            return new Decoded(data, (int) format.getSampleRate(),
                    format.getChannels() >= 2, format.getSampleSizeInBits() == 8);
        }
    }
}
