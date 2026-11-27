package io.github.pgatzka.shorty.sorting.service;

import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import org.springframework.stereotype.Component;

/** Selection sort. */
@Component
class SelectionSorter implements Sorter {

    @Override
    public SortAlgorithm algorithm() {
        return SortAlgorithm.SELECTION;
    }

    @Override
    public void sort(TrackedArray array) {
        int n = array.length();
        for (int i = 0; i < n - 1; i++) {
            int min = i;
            for (int j = i + 1; j < n; j++) {
                if (array.compare(j, min) < 0) {
                    min = j;
                }
            }
            if (min != i) {
                array.swap(i, min);
            }
        }
    }
}
