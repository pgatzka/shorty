package io.github.pgatzka.shorty.render.service;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Pipes raw BGR frames into ffmpeg and muxes them with a WAV track into an H.264/AAC MP4. */
@Component
public class FfmpegEncoder {

    private static final long FINISH_TIMEOUT_MINUTES = 15;
    private static final int LOG_TAIL_CHARS = 2000;
    private static final long CLOSE_TIMEOUT_SECONDS = 10;

    private final RenderProperties properties;

    public FfmpegEncoder(RenderProperties properties) {
        this.properties = properties;
    }

    /** An open encoding process; frames must be {@link BufferedImage#TYPE_3BYTE_BGR}. */
    public final class Session implements AutoCloseable {

        private final Process process;
        private final OutputStream input;
        private final Path log;
        private boolean finished;

        private Session(Process process, Path log) {
            this.process = process;
            this.input = new BufferedOutputStream(process.getOutputStream(), 1 << 20);
            this.log = log;
        }

        public void writeFrame(BufferedImage frame) {
            if (frame.getType() != BufferedImage.TYPE_3BYTE_BGR) {
                throw new IllegalArgumentException("frame must be TYPE_3BYTE_BGR");
            }
            byte[] pixels = ((DataBufferByte) frame.getRaster().getDataBuffer()).getData();
            try {
                input.write(pixels);
            } catch (IOException e) {
                throw new RenderException("ffmpeg stopped accepting frames: " + logTail(), e);
            }
        }

        /** Closes the input and waits for ffmpeg to finish writing the file. */
        public void finish() {
            try {
                input.close();
                if (!process.waitFor(FINISH_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                    process.destroyForcibly();
                    throw new RenderException("ffmpeg timed out");
                }
                if (process.exitValue() != 0) {
                    throw new RenderException("ffmpeg failed with exit code " + process.exitValue() + ": " + logTail());
                }
                finished = true;
            } catch (IOException e) {
                throw new RenderException("Could not finish encoding: " + logTail(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                throw new RenderException("Interrupted while encoding", e);
            }
        }

        @Override
        public void close() {
            if (!finished) {
                process.destroyForcibly();
                try {
                    process.waitFor(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            try {
                Files.deleteIfExists(log);
            } catch (IOException e) {
                // Windows keeps the file locked until the killed process is gone; clean up later.
                log.toFile().deleteOnExit();
            }
        }

        private String logTail() {
            try {
                String text = Files.readString(log, StandardCharsets.UTF_8).strip();
                return text.length() > LOG_TAIL_CHARS ? text.substring(text.length() - LOG_TAIL_CHARS) : text;
            } catch (IOException e) {
                return "(no ffmpeg log: " + e.getMessage() + ")";
            }
        }
    }

    public Session start(Path audio, Path output, int width, int height) {
        List<String> command = List.of(
                properties.ffmpegPath(),
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-f",
                "rawvideo",
                "-pix_fmt",
                "bgr24",
                "-s",
                width + "x" + height,
                "-r",
                Integer.toString(properties.fps()),
                "-i",
                "-",
                "-i",
                audio.toString(),
                "-c:v",
                "libx264",
                "-preset",
                properties.x264Preset(),
                "-crf",
                "20",
                "-pix_fmt",
                "yuv420p",
                "-c:a",
                "aac",
                "-b:a",
                "160k",
                "-movflags",
                "+faststart",
                "-shortest",
                output.toString());
        try {
            Path log = Files.createTempFile("shorty-ffmpeg-", ".log");
            Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(log.toFile())
                    .start();
            return new Session(process, log);
        } catch (IOException e) {
            throw new RenderException("Could not start ffmpeg (" + properties.ffmpegPath() + ")", e);
        }
    }
}
