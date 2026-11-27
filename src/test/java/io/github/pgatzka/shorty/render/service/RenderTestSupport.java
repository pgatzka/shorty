package io.github.pgatzka.shorty.render.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Shared settings for render tests: tiny frames so encoding is fast. */
final class RenderTestSupport {

    private RenderTestSupport() {}

    static RenderProperties tinyProperties(Path outputDir) {
        return new RenderProperties(
                108, 192, 10, 200, 6, 12, 20.0, 0.2, 0.3, 0.3, outputDir, "ffmpeg", 8000, "ultrafast");
    }

    static RenderProperties withFfmpeg(RenderProperties p, String ffmpeg) {
        return new RenderProperties(
                p.width(),
                p.height(),
                p.fps(),
                p.stepsPerSecond(),
                p.minBars(),
                p.maxBars(),
                p.maxDurationSeconds(),
                p.introSeconds(),
                p.sweepSeconds(),
                p.outroSeconds(),
                p.outputDir(),
                ffmpeg,
                p.sampleRate(),
                p.x264Preset());
    }

    static boolean ffmpegAvailable() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return process.waitFor(20, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
