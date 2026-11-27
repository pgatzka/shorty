package io.github.pgatzka.shorty.analytics.domain;

import java.time.Instant;

/**
 * Latest known statistics of an uploaded video.
 *
 * @param videoId YouTube video id
 * @param title video title
 * @param algorithm algorithm display name
 * @param uploadedAt upload time
 * @param views view count
 * @param likes like count
 * @param comments comment count
 * @param measuredAt when the statistics were read, or {@code null} if never
 */
public record VideoPerformance(
        String videoId,
        String title,
        String algorithm,
        Instant uploadedAt,
        long views,
        long likes,
        long comments,
        Instant measuredAt) {

    public String url() {
        return "https://youtube.com/shorts/" + videoId;
    }
}
