package io.github.pgatzka.shorty.render.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.shorty.render.domain.VisualStyle;
import io.github.pgatzka.shorty.render.service.FrameComposer.Overlay;
import io.github.pgatzka.shorty.render.service.FrameComposer.Phase;
import io.github.pgatzka.shorty.render.service.ReplayState.Highlight;
import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import io.github.pgatzka.shorty.sorting.domain.SortStep;
import io.github.pgatzka.shorty.sorting.domain.StepType;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FrameAndAudioTest {

    private static final List<StylePainter> PAINTERS = List.of(
            new ClassicBarsPainter(), new RainbowBarsPainter(), new ScatterDotsPainter(), new MirroredBarsPainter());

    private final FrameComposer composer = new FrameComposer(PAINTERS);

    @Test
    void replayAppliesStepsAndHighlights() {
        ReplayState state = new ReplayState(List.of(3, 1, 2), 3);

        state.apply(new SortStep(StepType.COMPARE, 0, 1, 1, 0, 2));
        assertThat(state.highlight(0)).isEqualTo(Highlight.COMPARE);
        assertThat(state.highlight(2)).isEqualTo(Highlight.NONE);

        assertThat(state.toneValue(new SortStep(StepType.SWAP, 0, 1, 1, 1, 6))).isEqualTo(3);
        state.apply(new SortStep(StepType.SWAP, 0, 1, 1, 1, 6));
        assertThat(state.value(0)).isEqualTo(1);
        assertThat(state.value(1)).isEqualTo(3);
        assertThat(state.highlight(0)).as("compare highlight wins").isEqualTo(Highlight.COMPARE);

        state.clearHighlights();
        state.apply(new SortStep(StepType.WRITE, 2, 7, 1, 1, 7));
        assertThat(state.toneValue(new SortStep(StepType.WRITE, 2, 9, 1, 1, 8))).isEqualTo(9);
        assertThat(state.value(2)).isEqualTo(7);
        assertThat(state.highlight(2)).isEqualTo(Highlight.CHANGE);
        assertThat(state.comparisons()).isEqualTo(1);
        assertThat(state.swaps()).isEqualTo(1);
        assertThat(state.accesses()).isEqualTo(7);

        state.setSortedCount(99);
        assertThat(state.highlight(2)).isEqualTo(Highlight.SORTED);
        assertThat(state.ratio(1)).isEqualTo(1.0);
        assertThat(state.maxValue()).isEqualTo(3);
    }

    @ParameterizedTest
    @EnumSource(VisualStyle.class)
    void everyStyleDrawsEveryPhase(VisualStyle style) {
        for (Phase phase : Phase.values()) {
            for (String handle : List.of("", "@channel")) {
                BufferedImage image = new BufferedImage(216, 384, BufferedImage.TYPE_3BYTE_BGR);
                ReplayState state = new ReplayState(List.of(5, 2, 4, 1, 3), 5);
                state.apply(new SortStep(StepType.COMPARE, 0, 1, 1, 0, 2));
                state.apply(new SortStep(StepType.SWAP, 2, 3, 1, 1, 6));
                state.setSortedCount(phase == Phase.SWEEP ? 2 : 0);

                composer.compose(image, state, new Overlay(SortAlgorithm.QUICK, style, handle, phase, 0.5));

                assertThat(distinctColors(image)).as("%s %s", style, phase).isGreaterThan(3);
            }
        }
    }

    @Test
    void manyBarsWithoutGaps() {
        BufferedImage image = new BufferedImage(108, 192, BufferedImage.TYPE_3BYTE_BGR);
        ReplayState state =
                new ReplayState(IntStream.rangeClosed(1, 150).boxed().toList(), 150);
        composer.compose(
                image, state, new Overlay(SortAlgorithm.BUBBLE, VisualStyle.CLASSIC_BARS, "", Phase.SORTING, 0));
        assertThat(distinctColors(image)).isGreaterThan(2);
    }

    @Test
    void missingPainterIsReported() {
        FrameComposer onlyClassic = new FrameComposer(List.of(new ClassicBarsPainter()));
        BufferedImage image = new BufferedImage(108, 192, BufferedImage.TYPE_3BYTE_BGR);
        ReplayState state = new ReplayState(List.of(1, 2), 2);
        Overlay overlay = new Overlay(SortAlgorithm.BUBBLE, VisualStyle.SCATTER_DOTS, "", Phase.INTRO, 0);
        assertThatThrownBy(() -> onlyClassic.compose(image, state, overlay)).isInstanceOf(RenderException.class);
    }

    @Test
    void stylesRotate() {
        assertThat(VisualStyle.CLASSIC_BARS.next()).isEqualTo(VisualStyle.RAINBOW_BARS);
        assertThat(VisualStyle.MIRRORED_BARS.next()).isEqualTo(VisualStyle.CLASSIC_BARS);
        assertThat(VisualStyle.SCATTER_DOTS.displayName()).isEqualTo("Scatter dots");
    }

    @Test
    void audioMixesTonesAndClips(@TempDir Path dir) throws Exception {
        AudioTrack track = new AudioTrack(8000, 1.0);
        track.addTone(0.1, 440, 0.05, 0.5);
        track.addTone(0.1, 440, 0.05, 0.9);
        track.addTone(0.98, 440, 0.1, 0.5); // runs past the end
        track.addTone(-0.01, 440, 0.02, 0.5); // starts before the beginning
        short[] pcm = track.toPcm16();

        assertThat(track.length()).isEqualTo(8000);
        assertThat(pcm).hasSize(8000);
        assertThat(maxAbs(pcm)).isEqualTo(Short.MAX_VALUE);
        assertThat(pcm[4000]).isZero();

        Path wav = dir.resolve("a.wav");
        WavWriter.write(wav, pcm, 8000);
        byte[] bytes = Files.readAllBytes(wav);
        assertThat(bytes).hasSize(44 + 16000);
        assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
        assertThat(new String(bytes, 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WAVE");
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(header.getInt(24)).isEqualTo(8000);
        assertThat(header.getInt(40)).isEqualTo(16000);
    }

    @Test
    void frequencyFollowsValue() {
        assertThat(VideoRenderer.frequency(0, 10)).isEqualTo(220.0);
        assertThat(VideoRenderer.frequency(10, 10)).isEqualTo(1100.0);
        assertThat(VideoRenderer.frequency(5, 0)).isGreaterThan(220.0);
    }

    private static long distinctColors(BufferedImage image) {
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        return Arrays.stream(pixels).distinct().count();
    }

    private static int maxAbs(short[] pcm) {
        int max = 0;
        for (short sample : pcm) {
            max = Math.max(max, Math.abs(sample));
        }
        return max;
    }
}
