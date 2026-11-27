package io.github.pgatzka.shorty.sorting.service;

import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import org.springframework.stereotype.Component;

/** Top-down merge sort with an auxiliary buffer. */
@Component
class MergeSorter implements Sorter {

    @Override
    public SortAlgorithm algorithm() {
        return SortAlgorithm.MERGE;
    }

    @Override
    public void sort(TrackedArray array) {
        int[] buffer = new int[array.length()];
        mergeSort(array, buffer, 0, array.length() - 1);
    }

    private void mergeSort(TrackedArray array, int[] buffer, int low, int high) {
        if (low >= high) {
            return;
        }
        int middle = low + (high - low) / 2;
        mergeSort(array, buffer, low, middle);
        mergeSort(array, buffer, middle + 1, high);
        merge(array, buffer, low, middle, high);
    }

    private void merge(TrackedArray array, int[] buffer, int low, int middle, int high) {
        for (int k = low; k <= high; k++) {
            buffer[k] = array.get(k);
        }
        int left = low;
        int right = middle + 1;
        for (int k = low; k <= high; k++) {
            boolean takeRight =
                    left > middle || right <= high && array.compareValues(buffer[right], buffer[left], right, left) < 0;
            if (takeRight) {
                array.set(k, buffer[right]);
                right++;
            } else {
                array.set(k, buffer[left]);
                left++;
            }
        }
    }
}
