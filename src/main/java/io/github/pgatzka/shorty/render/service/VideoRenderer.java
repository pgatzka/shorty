package io.github.pgatzka.shorty.render.service;

import io.github.pgatzka.shorty.render.domain.RenderRequest;
import io.github.pgatzka.shorty.render.domain.RenderedVideo;
import io.github.pgatzka.shorty.render.service.FrameComposer.Overlay;
import io.github.pgatzka.shorty.render.service.FrameComposer.Phase;
import io.github.pgatzka.shorty.sorting.domain.SortStep;
import io.github.pgatzka.shorty.sorting.domain.SortTrace;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Renders a sorting video end to end: plan, beeps, frames, encode. */
@Service
public class VideoRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(VideoRenderer.class);
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final double MIN_FREQUENCY = 220.0;
    private static final double FREQUENCY_RANGE = 880.0;
    private static final double TONE_AMPLITUDE = 0.22;
    private static final double MAX_TONE_SECONDS = 0.06;

    private final BarCountPlanner planner;
    private final FrameComposer composer;
    private final FfmpegEncoder encoder;
    private final RenderProperties properties;
    private final Clock clock;

    @Autowired
    public VideoRenderer(
            BarCountPlanner planner, FrameComposer composer, FfmpegEncoder encoder, RenderProperties properties) {
        this(planner, composer, encoder, properties, Clock.systemUTC());
    }

    VideoRenderer(
            BarCountPlanner planner,
            FrameComposer composer,
            FfmpegEncoder encoder,
            RenderProperties properties,
            Clock clock) {
        this.planner = planner;
        this.composer = composer;
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    /** Directory the MP4 files are written to. */
    public Path outputDirectory() {
        return properties.outputDir().toAbsolutePath().normalize();
    }

    public RenderedVideo render(RenderRequest request) {
        BarCountPlanner.Plan plan = planner.plan(request.algorithm(), request.seed());
        SortTrace trace = plan.trace();
        Timeline timeline = plan.timeline();
        LOG.info(
                "Rendering {} / {} with {} bars, {} steps, {} s",
                request.algorithm(),
                request.style(),
                trace.size(),
                trace.stepCount(),
                String.format(Locale.ROOT, "%.1f", timeline.durationSeconds()));
        try {
            Path directory = Files.createDirectories(outputDirectory());
            Path output = directory.resolve(fileName(request));
            Path audio = Files.createTempFile("shorty-audio-", ".wav");
            try {
                WavWriter.write(audio, buildAudio(trace, timeline).toPcm16(), properties.sampleRate());
                encodeFrames(trace, timeline, request, audio, output);
            } finally {
                Files.deleteIfExists(audio);
            }
            return new RenderedVideo(
                    output,
                    request.algorithm(),
                    request.style(),
                    trace.size(),
                    timeline.durationSeconds(),
                    trace.comparisons(),
                    trace.swaps(),
                    trace.accesses());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    AudioTrack buildAudio(SortTrace trace, Timeline timeline) {
        AudioTrack track = new AudioTrack(properties.sampleRate(), timeline.durationSeconds());
        ReplayState state = new ReplayState(trace.initial(), trace.maxValue());
        double stepSeconds = 1.0 / properties.stepsPerSecond();
        double toneSeconds = Math.min(MAX_TONE_SECONDS, stepSeconds * 1.5);
        List<SortStep> steps = trace.steps();
        for (int k = 0; k < steps.size(); k++) {
            SortStep step = steps.get(k);
            double start = timeline.introSeconds() + k * stepSeconds;
            track.addTone(start, frequency(state.toneValue(step), state.maxValue()), toneSeconds, TONE_AMPLITUDE);
            state.apply(step);
        }
        double sweepStart = timeline.introSeconds() + timeline.sortSeconds();
        double sweepStep = timeline.sweepSeconds() / Math.max(1, trace.size());
        for (int i = 0; i < trace.size(); i++) {
            track.addTone(
                    sweepStart + i * sweepStep,
                    frequency(state.value(i), state.maxValue()),
                    Math.min(MAX_TONE_SECONDS, sweepStep * 1.5),
                    TONE_AMPLITUDE);
        }
        return track;
    }

    private void encodeFrames(SortTrace trace, Timeline timeline, RenderRequest request, Path audio, Path output)
            throws IOException {
        int width = properties.width();
        int height = properties.height();
        BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        ReplayState state = new ReplayState(trace.initial(), trace.maxValue());
        List<SortStep> steps = trace.steps();
        try (FfmpegEncoder.Session session = encoder.start(audio, output, width, height)) {
            for (int f = 0; f < timeline.introFrames(); f++) {
                writeFrame(session, frame, state, request, Phase.INTRO, progress(f, timeline.introFrames()));
            }
            for (int f = 0; f < timeline.sortFrames(); f++) {
                state.clearHighlights();
                for (int k = timeline.firstStep(f); k < timeline.endStep(f); k++) {
                    state.apply(steps.get(k));
                }
                writeFrame(session, frame, state, request, Phase.SORTING, progress(f, timeline.sortFrames()));
            }
            state.clearHighlights();
            for (int f = 0; f < timeline.sweepFrames(); f++) {
                state.setSortedCount((int) Math.ceil((f + 1.0) * trace.size() / timeline.sweepFrames()));
                writeFrame(session, frame, state, request, Phase.SWEEP, progress(f, timeline.sweepFrames()));
            }
            state.setSortedCount(trace.size());
            for (int f = 0; f < timeline.outroFrames(); f++) {
                writeFrame(session, frame, state, request, Phase.OUTRO, progress(f, timeline.outroFrames()));
            }
            session.finish();
        }
    }

    private void writeFrame(
            FfmpegEncoder.Session session,
            BufferedImage frame,
            ReplayState state,
            RenderRequest request,
            Phase phase,
            double progress) {
        composer.compose(
                frame,
                state,
                new Overlay(request.algorithm(), request.style(), request.channelHandle(), phase, progress));
        session.writeFrame(frame);
    }

    private static double progress(int frame, int frames) {
        return frames <= 1 ? 1.0 : (double) frame / (frames - 1);
    }

    static double frequency(int value, int maxValue) {
        return MIN_FREQUENCY + FREQUENCY_RANGE * value / Math.max(1, maxValue);
    }

    private String fileName(RenderRequest request) {
        return "shorty-%s-%s-%s.mp4"
                .formatted(
                        FILE_TIME.format(clock.instant()),
                        request.algorithm().name().toLowerCase(Locale.ROOT),
                        request.style().name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}
