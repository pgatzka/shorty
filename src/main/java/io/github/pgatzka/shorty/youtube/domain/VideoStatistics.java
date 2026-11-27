package io.github.pgatzka.shorty.youtube.domain;

/** Public counters of one video. */
public record VideoStatistics(String videoId, long views, long likes, long comments) {}
