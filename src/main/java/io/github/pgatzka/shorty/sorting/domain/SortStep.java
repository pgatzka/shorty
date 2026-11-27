package io.github.pgatzka.shorty.sorting.domain;

import java.util.Objects;

/**
 * One visible step of a sort.
 *
 * <p>For {@link StepType#COMPARE} and {@link StepType#SWAP}, {@code first} and {@code second} are
 * positions. For {@link StepType#WRITE}, {@code first} is the position and {@code second} is the
 * value written. The counters are cumulative totals after this step.
 */
public record SortStep(StepType type, int first, int second, long comparisons, long swaps, long accesses) {

    public SortStep {
        Objects.requireNonNull(type, "type");
    }
}
