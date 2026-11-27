package io.github.pgatzka.shorty.sorting.service;

import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import io.github.pgatzka.shorty.sorting.domain.SortTrace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.stereotype.Service;

/** Produces sort traces for the renderer. */
@Service
public class SortingService {

    private final Map<SortAlgorithm, Sorter> sorters = new EnumMap<>(SortAlgorithm.class);

    public SortingService(List<Sorter> sorters) {
        sorters.forEach(sorter -> this.sorters.put(sorter.algorithm(), sorter));
    }

    /** Values 1..size in a random order that is reproducible for the same seed. */
    public List<Integer> shuffledInput(int size, long seed) {
        if (size < 1) {
            throw new IllegalArgumentException("size must be positive: " + size);
        }
        List<Integer> values = new ArrayList<>(size);
        for (int i = 1; i <= size; i++) {
            values.add(i);
        }
        Collections.shuffle(values, new Random(seed));
        return values;
    }

    /** Sorts the input and records every step. */
    public SortTrace trace(SortAlgorithm algorithm, List<Integer> input) {
        Sorter sorter = sorters.get(algorithm);
        if (sorter == null) {
            throw new IllegalStateException("No sorter registered for " + algorithm);
        }
        TrackedArray array = new TrackedArray(input);
        sorter.sort(array);
        return new SortTrace(algorithm, input, array.steps(), array.snapshot());
    }
}
