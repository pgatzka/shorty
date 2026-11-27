package io.github.pgatzka.shorty.sorting.service;

import java.util.List;

/** Test helper: a {@link SortingService} with every algorithm, usable from other packages. */
public final class SortingFixtures {

    private SortingFixtures() {}

    public static SortingService sortingService() {
        return new SortingService(List.of(
                new BubbleSorter(),
                new QuickSorter(),
                new MergeSorter(),
                new InsertionSorter(),
                new SelectionSorter()));
    }
}
