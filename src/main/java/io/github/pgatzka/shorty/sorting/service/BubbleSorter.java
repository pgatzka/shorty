package io.github.pgatzka.shorty.sorting.service;

import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import org.springframework.stereotype.Component;

/** Bubble sort with early exit when a pass makes no swaps. */
@Component
class BubbleSorter implements Sorter {

    @Override
    public SortAlgorithm algorithm() {
        return SortAlgorithm.BUBBLE;
    }

    @Override
    public void sort(TrackedArray array) {
        boolean swapped = true;
        for (int end = array.length() - 1; end > 0 && swapped; end--) {
            swapped = false;
            for (int i = 0; i < end; i++) {
                if (array.compare(i, i + 1) > 0) {
                    array.swap(i, i + 1);
                    swapped = true;
                }
            }
        }
    }
}
