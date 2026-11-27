package io.github.pgatzka.shorty.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Statistics of one video at one point in time. */
@Entity
@Table(name = "stats_snapshot")
public class StatsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "video_id", nullable = false)
    private String videoId;

    @Column(name = "taken_at", nullable = false)
    private Instant takenAt;

    @Column(name = "views", nullable = false)
    private long views;

    @Column(name = "likes", nullable = false)
    private long likes;

    @Column(name = "comments", nullable = false)
    private long comments;

    protected StatsSnapshot() {
        // for JPA
    }

    public StatsSnapshot(String videoId, Instant takenAt, long views, long likes, long comments) {
        this.videoId = videoId;
        this.takenAt = takenAt;
        this.views = views;
        this.likes = likes;
        this.comments = comments;
    }

    public Long getId() {
        return id;
    }

    public String getVideoId() {
        return videoId;
    }

    public Instant getTakenAt() {
        return takenAt;
    }

    public long getViews() {
        return views;
    }

    public long getLikes() {
        return likes;
    }

    public long getComments() {
        return comments;
    }
}
