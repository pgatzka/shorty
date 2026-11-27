package io.github.pgatzka.shorty.render.service;

import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import io.github.pgatzka.shorty.sorting.domain.SortTrace;
import io.github.pgatzka.shorty.sorting.service.SortingService;
import org.springframework.stereotype.Component;

/**
 * Picks the largest bar count whose video, at the fixed playback speed, stays within the
 * configured maximum duration.
 */
@Component
public class BarCountPlanner {

    private final SortingService sortingService;
    private final RenderProperties properties;

    public BarCountPlanner(SortingService sortingService, RenderProperties properties) {
        this.sortingService = sortingService;
        this.properties = properties;
    }

    /** A chosen trace and its frame layout. */
    public record Plan(SortTrace trace, Timeline timeline) {}

    public Plan plan(SortAlgorithm algorithm, long seed) {
        int low = properties.minBars();
        int high = properties.maxBars();
        Plan best = planFor(algorithm, low, seed);
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            Plan candidate = planFor(algorithm, middle, seed);
            if (fits(candidate)) {
                best = candidate;
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        if (!fits(best)) {
            throw new RenderException(
                    "Even " + properties.minBars() + " bars exceed the maximum duration for " + algorithm);
        }
        return best;
    }

    private Plan planFor(SortAlgorithm algorithm, int bars, long seed) {
        SortTrace trace = sortingService.trace(algorithm, sortingService.shuffledInput(bars, seed));
        return new Plan(trace, Timeline.of(properties, trace.stepCount()));
    }

    private boolean fits(Plan plan) {
        return plan.timeline().durationSeconds() <= properties.maxDurationSeconds();
    }
}
