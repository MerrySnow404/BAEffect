package com.sighs.baeffect;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.sounds.JOrbisAudioStream;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Minecraft 1.21.1+ replaced {@code com.mojang.blaze3d.audio.OggAudioStream}
 * with {@code JOrbisAudioStream}, which emits float samples.
 */
public final class OggDecoder {
    public record Decoded(byte[] data, int sampleRate, boolean stereo, boolean eightBit) {
    }

    private OggDecoder() {
    }

    public static Decoded decode(byte[] ogg) throws IOException {
        try (JOrbisAudioStream stream = new JOrbisAudioStream(new ByteArrayInputStream(ogg))) {
            AudioFormat format = stream.getFormat();
            int channels = Math.max(1, format.getChannels());

            FloatArrayList samples = new FloatArrayList();
            while (stream.readChunk(samples::add)) {
                // readChunk appends one chunk per call
            }

            ByteBuffer pcm = ByteBuffer.allocate(samples.size() * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < samples.size(); i++) {
                float sample = Math.max(-1.0F, Math.min(1.0F, samples.getFloat(i)));
                pcm.putShort((short) Math.round(sample * 32767.0F));
            }
            return new Decoded(pcm.array(), (int) format.getSampleRate(), channels >= 2, false);
        }
    }
}
