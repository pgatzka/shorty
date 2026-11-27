package io.github.pgatzka.shorty.sorting.service;

import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import org.springframework.stereotype.Component;

/** Quick sort with Lomuto partitioning and a middle pivot (moved to the end first). */
@Component
class QuickSorter implements Sorter {

    @Override
    public SortAlgorithm algorithm() {
        return SortAlgorithm.QUICK;
    }

    @Override
    public void sort(TrackedArray array) {
        quickSort(array, 0, array.length() - 1);
    }

    private void quickSort(TrackedArray array, int low, int high) {
        if (low >= high) {
            return;
        }
        int pivot = partition(array, low, high);
        quickSort(array, low, pivot - 1);
        quickSort(array, pivot + 1, high);
    }

    private int partition(TrackedArray array, int low, int high) {
        int middle = low + (high - low) / 2;
        if (middle != high) {
            array.swap(middle, high);
        }
        int store = low;
        for (int i = low; i < high; i++) {
            if (array.compare(i, high) < 0) {
                if (i != store) {
                    array.swap(i, store);
                }
                store++;
            }
        }
        if (store != high) {
            array.swap(store, high);
        }
        return store;
    }
}
