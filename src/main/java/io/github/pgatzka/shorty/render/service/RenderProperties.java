package io.github.pgatzka.shorty.render.service;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Render settings ({@code shorty.render.*}).
 *
 * @param width frame width in pixels
 * @param height frame height in pixels
 * @param fps frames per second
 * @param stepsPerSecond fixed playback speed in sort steps per second
 * @param minBars smallest bar count
 * @param maxBars largest bar count
 * @param maxDurationSeconds hard ceiling for the whole video; must stay below the Shorts limit
 * @param introSeconds still frames before sorting starts
 * @param sweepSeconds length of the final "sorted" sweep
 * @param outroSeconds length of the follow call-to-action card
 * @param outputDir where MP4 files are written
 * @param ffmpegPath ffmpeg executable
 * @param sampleRate audio sample rate
 * @param x264Preset libx264 speed/size preset
 */
@ConfigurationProperties("shorty.render")
public record RenderProperties(
        @DefaultValue("1080") int width,
        @DefaultValue("1920") int height,
        @DefaultValue("30") int fps,
        @DefaultValue("60") int stepsPerSecond,
        @DefaultValue("10") int minBars,
        @DefaultValue("150") int maxBars,
        @DefaultValue("175") double maxDurationSeconds,
        @DefaultValue("1.0") double introSeconds,
        @DefaultValue("2.5") double sweepSeconds,
        @DefaultValue("3.0") double outroSeconds,
        @DefaultValue("./videos") Path outputDir,
        @DefaultValue("ffmpeg") String ffmpegPath,
        @DefaultValue("44100") int sampleRate,
        @DefaultValue("veryfast") String x264Preset) {

    /** Shorts are limited to three minutes. */
    public static final double SHORTS_LIMIT_SECONDS = 180.0;

    public RenderProperties {
        if (width <= 0 || height <= 0 || width % 2 != 0 || height % 2 != 0) {
            throw new IllegalArgumentException("width and height must be positive and even");
        }
        if (fps <= 0 || stepsPerSecond <= 0 || sampleRate <= 0) {
            throw new IllegalArgumentException("fps, stepsPerSecond and sampleRate must be positive");
        }
        if (minBars < 2 || maxBars < minBars) {
            throw new IllegalArgumentException("need 2 <= minBars <= maxBars");
        }
        if (maxDurationSeconds <= 0 || maxDurationSeconds >= SHORTS_LIMIT_SECONDS) {
            throw new IllegalArgumentException("maxDurationSeconds must be in (0, 180)");
        }
    }
}
