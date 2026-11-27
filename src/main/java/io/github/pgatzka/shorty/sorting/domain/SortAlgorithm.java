package io.github.pgatzka.shorty.sorting.domain;

/** The sorting algorithms Shorty can visualize. */
public enum SortAlgorithm {
    BUBBLE("Bubble Sort", "O(n²)"),
    QUICK("Quick Sort", "O(n log n)"),
    MERGE("Merge Sort", "O(n log n)"),
    INSERTION("Insertion Sort", "O(n²)"),
    SELECTION("Selection Sort", "O(n²)");

    private final String displayName;
    private final String bigO;

    SortAlgorithm(String displayName, String bigO) {
        this.displayName = displayName;
        this.bigO = bigO;
    }

    public String displayName() {
        return displayName;
    }

    /** Average-case time complexity shown in the video. */
    public String bigO() {
        return bigO;
    }

    /** The algorithm after this one, wrapping around, used for rotation. */
    public SortAlgorithm next() {
        SortAlgorithm[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
