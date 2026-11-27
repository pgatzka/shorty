package io.github.pgatzka.shorty.analytics.domain;

import java.util.List;

/** Sums over all tracked videos. */
public record AnalyticsTotals(int videos, long views, long likes, long comments) {

    public static AnalyticsTotals of(List<VideoPerformance> performances) {
        return new AnalyticsTotals(
                performances.size(),
                performances.stream().mapToLong(VideoPerformance::views).sum(),
                performances.stream().mapToLong(VideoPerformance::likes).sum(),
                performances.stream().mapToLong(VideoPerformance::comments).sum());
    }
}
