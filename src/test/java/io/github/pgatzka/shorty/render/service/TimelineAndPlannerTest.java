package io.github.pgatzka.shorty.render.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import io.github.pgatzka.shorty.sorting.service.SortingFixtures;
import io.github.pgatzka.shorty.sorting.service.SortingService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TimelineAndPlannerTest {

    private static final RenderProperties DEFAULTS = new RenderProperties(
            1080, 1920, 30, 60, 10, 150, 175, 1.0, 2.5, 3.0, Path.of("videos"), "ffmpeg", 44100, "veryfast");

    private final SortingService sorting = SortingFixtures.sortingService();

    @Test
    void timelineSplitsStepsAcrossFrames() {
        Timeline timeline = Timeline.of(DEFAULTS, 100);

        assertThat(timeline.stepsPerFrame()).isEqualTo(2.0);
        assertThat(timeline.introFrames()).isEqualTo(30);
        assertThat(timeline.sortFrames()).isEqualTo(50);
        assertThat(timeline.sweepFrames()).isEqualTo(75);
        assertThat(timeline.outroFrames()).isEqualTo(90);
        assertThat(timeline.totalFrames()).isEqualTo(245);
        assertThat(timeline.durationSeconds()).isEqualTo(245 / 30.0);
        assertThat(timeline.introSeconds()).isEqualTo(1.0);
        assertThat(timeline.sortSeconds()).isEqualTo(50 / 30.0);
        assertThat(timeline.sweepSeconds()).isEqualTo(2.5);
        assertThat(timeline.firstStep(0)).isZero();
        assertThat(timeline.endStep(0)).isEqualTo(2);
        assertThat(timeline.endStep(49)).isEqualTo(100);
    }

    @Test
    void everyStepIsShownExactlyOnce() {
        Timeline timeline = Timeline.of(DEFAULTS, 101);
        int shown = 0;
        for (int frame = 0; frame < timeline.sortFrames(); frame++) {
            assertThat(timeline.firstStep(frame)).isEqualTo(shown);
            shown = timeline.endStep(frame);
        }
        assertThat(shown).isEqualTo(101);
    }

    @Test
    void timelineHasAtLeastOneSortFrame() {
        assertThat(Timeline.of(DEFAULTS, 0).sortFrames()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(SortAlgorithm.class)
    void plannerStaysUnderTheShortsLimit(SortAlgorithm algorithm) {
        BarCountPlanner.Plan plan = new BarCountPlanner(sorting, DEFAULTS).plan(algorithm, 1234);

        assertThat(plan.timeline().durationSeconds())
                .isLessThanOrEqualTo(175)
                .isLessThan(RenderProperties.SHORTS_LIMIT_SECONDS);
        assertThat(plan.trace().size()).isBetween(10, 150);
    }

    @Test
    void plannerPicksTheLargestBarCountThatFits() {
        BarCountPlanner.Plan plan = new BarCountPlanner(sorting, DEFAULTS).plan(SortAlgorithm.BUBBLE, 99);
        int bars = plan.trace().size();
        assertThat(bars).isLessThan(150);

        var bigger = sorting.trace(SortAlgorithm.BUBBLE, sorting.shuffledInput(bars + 1, 99));
        assertThat(Timeline.of(DEFAULTS, bigger.stepCount()).durationSeconds()).isGreaterThan(175);
    }

    @Test
    void plannerUsesMaxBarsForFastAlgorithms() {
        BarCountPlanner.Plan plan = new BarCountPlanner(sorting, DEFAULTS).plan(SortAlgorithm.MERGE, 5);
        assertThat(plan.trace().size()).isEqualTo(150);
    }

    @Test
    void plannerFailsWhenNothingFits() {
        RenderProperties tooShort = new RenderProperties(
                1080, 1920, 30, 1, 10, 20, 5.0, 1.0, 1.0, 1.0, Path.of("v"), "ffmpeg", 44100, "veryfast");
        BarCountPlanner planner = new BarCountPlanner(sorting, tooShort);
        assertThatThrownBy(() -> planner.plan(SortAlgorithm.BUBBLE, 1)).isInstanceOf(RenderException.class);
    }

    @Test
    void propertiesAreValidated() {
        assertThatThrownBy(() ->
                        new RenderProperties(1081, 1920, 30, 60, 10, 150, 175, 1, 1, 1, Path.of("v"), "f", 44100, "p"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new RenderProperties(0, 1920, 30, 60, 10, 150, 175, 1, 1, 1, Path.of("v"), "f", 44100, "p"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new RenderProperties(1080, 1921, 30, 60, 10, 150, 175, 1, 1, 1, Path.of("v"), "f", 44100, "p"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new RenderProperties(1080, 1920, 0, 60, 10, 150, 175, 1, 1, 1, Path.of("v"), "f", 44100, "p"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new RenderProperties(1080, 1920, 30, 0, 10, 150, 175, 1, 1, 1, Path.of("v"), "f", 44100, "p"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new RenderProperties(1080, 1920, 30, 60, 10, 150, 175, 1, 1, 1, Path.of("v"), "f", 0, "p"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new RenderProperties(1080, 1920, 30, 60, 1, 150, 175, 1, 1, 1, Path.of("v"), "f", 44100, "p"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new RenderProperties(1080, 1920, 30, 60, 10, 5, 175, 1, 1, 1, Path.of("v"), "f", 44100, "p"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new RenderProperties(1080, 1920, 30, 60, 10, 150, 180, 1, 1, 1, Path.of("v"), "f", 44100, "p"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new RenderProperties(1080, 1920, 30, 60, 10, 150, 0, 1, 1, 1, Path.of("v"), "f", 44100, "p"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
