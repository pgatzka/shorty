package io.github.pgatzka.shorty.render.service;

import io.github.pgatzka.shorty.sorting.domain.SortStep;
import io.github.pgatzka.shorty.sorting.domain.StepType;
import java.util.Arrays;
import java.util.List;

/** Mutable replay of a sort trace: current values, counters and highlights for one frame. */
final class ReplayState {

    /** How a single bar should be highlighted. */
    enum Highlight {
        NONE,
        COMPARE,
        CHANGE,
        SORTED
    }

    private final int[] values;
    private final Highlight[] highlights;
    private final int maxValue;
    private long comparisons;
    private long swaps;
    private long accesses;
    private int sortedCount;

    ReplayState(List<Integer> initial, int maxValue) {
        this.values = initial.stream().mapToInt(Integer::intValue).toArray();
        this.highlights = new Highlight[values.length];
        this.maxValue = Math.max(1, maxValue);
        clearHighlights();
    }

    /** Applies a step and highlights the positions it touched. */
    void apply(SortStep step) {
        switch (step.type()) {
            case COMPARE -> {
                mark(step.first(), Highlight.COMPARE);
                mark(step.second(), Highlight.COMPARE);
            }
            case SWAP -> {
                int tmp = values[step.first()];
                values[step.first()] = values[step.second()];
                values[step.second()] = tmp;
                mark(step.first(), Highlight.CHANGE);
                mark(step.second(), Highlight.CHANGE);
            }
            case WRITE -> {
                values[step.first()] = step.second();
                mark(step.first(), Highlight.CHANGE);
            }
        }
        comparisons = step.comparisons();
        swaps = step.swaps();
        accesses = step.accesses();
    }

    /** The value whose pitch represents this step, read before the step is applied. */
    int toneValue(SortStep step) {
        return step.type() == StepType.WRITE ? step.second() : values[step.first()];
    }

    void clearHighlights() {
        Arrays.fill(highlights, Highlight.NONE);
    }

    void setSortedCount(int sortedCount) {
        this.sortedCount = Math.clamp(sortedCount, 0, values.length);
    }

    private void mark(int index, Highlight highlight) {
        if (index >= 0 && index < highlights.length && highlights[index] != Highlight.COMPARE) {
            highlights[index] = highlight;
        }
    }

    int size() {
        return values.length;
    }

    int value(int index) {
        return values[index];
    }

    int maxValue() {
        return maxValue;
    }

    /** Value at index scaled to (0, 1]. */
    double ratio(int index) {
        return (double) values[index] / maxValue;
    }

    Highlight highlight(int index) {
        if (index < sortedCount) {
            return Highlight.SORTED;
        }
        return highlights[index];
    }

    long comparisons() {
        return comparisons;
    }

    long swaps() {
        return swaps;
    }

    long accesses() {
        return accesses;
    }
}
