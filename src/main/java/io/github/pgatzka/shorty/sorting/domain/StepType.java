package io.github.pgatzka.shorty.sorting.domain;

/** Kind of a visible sorting step. */
public enum StepType {
    /** Two positions were compared. */
    COMPARE,
    /** Two positions were swapped. */
    SWAP,
    /** A value was written to a position. */
    WRITE
}
