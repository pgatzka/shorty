package io.github.pgatzka.shorty.sorting.service;

import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import org.springframework.stereotype.Component;

/** Insertion sort using adjacent swaps, so every move is visible. */
@Component
class InsertionSorter implements Sorter {

    @Override
    public SortAlgorithm algorithm() {
        return SortAlgorithm.INSERTION;
    }

    @Override
    public void sort(TrackedArray array) {
        for (int i = 1; i < array.length(); i++) {
            for (int j = i; j > 0 && array.compare(j - 1, j) > 0; j--) {
                array.swap(j - 1, j);
            }
        }
    }
}
