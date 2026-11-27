package io.github.pgatzka.shorty.render.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.shorty.render.domain.RenderRequest;
import io.github.pgatzka.shorty.render.domain.RenderedVideo;
import io.github.pgatzka.shorty.render.domain.VisualStyle;
import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import io.github.pgatzka.shorty.sorting.service.SortingFixtures;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Encodes real (tiny) videos; needs ffmpeg on the PATH, as in CI and the Docker image. */
class VideoRendererTest {

    @TempDir
    Path dir;

    @BeforeAll
    static void requireFfmpeg() {
        assertThat(RenderTestSupport.ffmpegAvailable())
                .as("ffmpeg must be installed to run the render tests")
                .isTrue();
    }

    private VideoRenderer renderer(RenderProperties properties) {
        return new VideoRenderer(
                new BarCountPlanner(SortingFixtures.sortingService(), properties),
                new FrameComposer(List.of(
                        new ClassicBarsPainter(),
                        new RainbowBarsPainter(),
                        new ScatterDotsPainter(),
                        new MirroredBarsPainter())),
                new FfmpegEncoder(properties),
                properties,
                Clock.fixed(Instant.parse("2026-11-27T10:15:30.123Z"), ZoneOffset.UTC));
    }

    @Test
    void rendersAnMp4WithAudio() throws Exception {
        RenderProperties properties = RenderTestSupport.tinyProperties(dir.resolve("out"));
        RenderedVideo video = renderer(properties)
                .render(new RenderRequest(SortAlgorithm.QUICK, VisualStyle.RAINBOW_BARS, 5, " @me "));

        assertThat(video.file()).exists();
        assertThat(Files.size(video.file())).isGreaterThan(1000);
        assertThat(video.fileName()).isEqualTo("shorty-20261127-101530-123-quick-rainbow-bars.mp4");
        assertThat(video.barCount()).isBetween(6, 12);
        assertThat(video.durationSeconds()).isPositive().isLessThanOrEqualTo(20);
        assertThat(video.comparisons()).isPositive();
        assertThat(video.algorithm()).isEqualTo(SortAlgorithm.QUICK);
        assertThat(video.style()).isEqualTo(VisualStyle.RAINBOW_BARS);
        assertThat(renderer(properties).outputDirectory())
                .isEqualTo(dir.resolve("out").toAbsolutePath());
    }

    @Test
    void missingFfmpegIsReported() {
        RenderProperties properties = RenderTestSupport.withFfmpeg(
                RenderTestSupport.tinyProperties(dir), dir.resolve("no-ffmpeg").toString());
        VideoRenderer renderer = renderer(properties);
        RenderRequest request = new RenderRequest(SortAlgorithm.BUBBLE, VisualStyle.CLASSIC_BARS, 1, null);

        assertThatThrownBy(() -> renderer.render(request))
                .isInstanceOf(RenderException.class)
                .hasMessageContaining("Could not start ffmpeg");
    }

    @Test
    void ffmpegErrorsAreReported() {
        RenderProperties properties = RenderTestSupport.tinyProperties(dir);
        FfmpegEncoder encoder = new FfmpegEncoder(properties);
        // An audio input that does not exist makes ffmpeg exit with an error.
        FfmpegEncoder.Session session = encoder.start(dir.resolve("missing.wav"), dir.resolve("x.mp4"), 108, 192);
        BufferedImage frame = new BufferedImage(108, 192, BufferedImage.TYPE_3BYTE_BGR);

        assertThatThrownBy(() -> {
                    for (int i = 0; i < 200; i++) {
                        session.writeFrame(frame);
                    }
                    session.finish();
                })
                .isInstanceOf(RenderException.class);
        assertThatCode(session::close).doesNotThrowAnyException();
    }

    @Test
    void framesMustBeBgr() throws Exception {
        RenderProperties properties = RenderTestSupport.tinyProperties(dir);
        try (FfmpegEncoder.Session session =
                new FfmpegEncoder(properties).start(dir.resolve("a.wav"), dir.resolve("y.mp4"), 108, 192)) {
            BufferedImage rgb = new BufferedImage(108, 192, BufferedImage.TYPE_INT_RGB);
            assertThatThrownBy(() -> session.writeFrame(rgb)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void requestNormalizesHandle() {
        assertThat(new RenderRequest(SortAlgorithm.BUBBLE, VisualStyle.CLASSIC_BARS, 1, null).channelHandle())
                .isEmpty();
        assertThat(new RenderRequest(SortAlgorithm.BUBBLE, VisualStyle.CLASSIC_BARS, 1, " @a ").channelHandle())
                .isEqualTo("@a");
    }
}
