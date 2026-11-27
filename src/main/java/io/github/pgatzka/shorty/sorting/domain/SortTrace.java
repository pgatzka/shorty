package io.github.pgatzka.shorty.sorting.domain;

import java.util.List;
import java.util.Objects;

/** The full, replayable record of sorting one input. */
public record SortTrace(SortAlgorithm algorithm, List<Integer> initial, List<SortStep> steps, List<Integer> result) {

    public SortTrace {
        Objects.requireNonNull(algorithm, "algorithm");
        initial = List.copyOf(initial);
        steps = List.copyOf(steps);
        result = List.copyOf(result);
    }

    public int size() {
        return initial.size();
    }

    public int stepCount() {
        return steps.size();
    }

    /** Largest value in the input, used to scale bar heights and tones. */
    public int maxValue() {
        return initial.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    public long comparisons() {
        return steps.isEmpty() ? 0 : steps.getLast().comparisons();
    }

    public long swaps() {
        return steps.isEmpty() ? 0 : steps.getLast().swaps();
    }

    public long accesses() {
        return steps.isEmpty() ? 0 : steps.getLast().accesses();
    }
}
