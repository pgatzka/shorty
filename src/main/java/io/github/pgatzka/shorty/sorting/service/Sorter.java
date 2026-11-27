package io.github.pgatzka.shorty.sorting.service;

import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;

/** A sorting algorithm working on a {@link TrackedArray}. */
public interface Sorter {

    SortAlgorithm algorithm();

    /** Sorts the array ascending in place. */
    void sort(TrackedArray array);
}
