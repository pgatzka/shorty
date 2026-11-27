package io.github.pgatzka.shorty.render.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes 16-bit mono PCM as a WAV file. */
final class WavWriter {

    private static final int HEADER_SIZE = 44;

    private WavWriter() {}

    static void write(Path file, short[] pcm, int sampleRate) throws IOException {
        int dataSize = pcm.length * 2;
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(36 + dataSize);
        buffer.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buffer.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(16); // fmt chunk size
        buffer.putShort((short) 1); // PCM
        buffer.putShort((short) 1); // mono
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * 2); // byte rate
        buffer.putShort((short) 2); // block align
        buffer.putShort((short) 16); // bits per sample
        buffer.put("data".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(dataSize);
        for (short sample : pcm) {
            buffer.putShort(sample);
        }
        Files.write(file, buffer.array());
    }
}
