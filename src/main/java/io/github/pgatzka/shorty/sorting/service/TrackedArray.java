package io.github.pgatzka.shorty.sorting.service;

import io.github.pgatzka.shorty.sorting.domain.SortStep;
import io.github.pgatzka.shorty.sorting.domain.StepType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An int array that counts every comparison, swap and array access and records visible steps.
 *
 * <p>Reads are counted but not recorded as steps; they show up in the counters of the next step.
 */
public final class TrackedArray {

    private final int[] values;
    private final List<SortStep> steps = new ArrayList<>();
    private long comparisons;
    private long swaps;
    private long accesses;

    public TrackedArray(List<Integer> input) {
        this.values = input.stream().mapToInt(Integer::intValue).toArray();
    }

    public int length() {
        return values.length;
    }

    /** Reads a value (counted as one array access). */
    public int get(int index) {
        accesses++;
        return values[index];
    }

    /** Compares the values at two positions; returns a negative, zero or positive number. */
    public int compare(int i, int j) {
        comparisons++;
        accesses += 2;
        record(StepType.COMPARE, i, j);
        return Integer.compare(values[i], values[j]);
    }

    /**
     * Compares two values that were already read (for example from a buffer), highlighting the
     * given positions.
     */
    public int compareValues(int left, int right, int highlightFirst, int highlightSecond) {
        comparisons++;
        record(StepType.COMPARE, highlightFirst, highlightSecond);
        return Integer.compare(left, right);
    }

    public void swap(int i, int j) {
        swaps++;
        accesses += 4;
        int tmp = values[i];
        values[i] = values[j];
        values[j] = tmp;
        record(StepType.SWAP, i, j);
    }

    public void set(int index, int value) {
        accesses++;
        values[index] = value;
        record(StepType.WRITE, index, value);
    }

    public List<SortStep> steps() {
        return List.copyOf(steps);
    }

    public List<Integer> snapshot() {
        return Arrays.stream(values).boxed().toList();
    }

    private void record(StepType type, int first, int second) {
        steps.add(new SortStep(type, first, second, comparisons, swaps, accesses));
    }
}
