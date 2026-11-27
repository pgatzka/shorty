package io.github.pgatzka.shorty.render.domain;

import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import java.nio.file.Path;

/** A finished MP4 file plus the facts shown in it. */
public record RenderedVideo(
        Path file,
        SortAlgorithm algorithm,
        VisualStyle style,
        int barCount,
        double durationSeconds,
        long comparisons,
        long swaps,
        long accesses) {

    public String fileName() {
        return file.getFileName().toString();
    }
}
