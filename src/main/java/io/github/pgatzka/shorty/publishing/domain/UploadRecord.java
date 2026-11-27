package io.github.pgatzka.shorty.publishing.domain;

import io.github.pgatzka.shorty.render.domain.RenderedVideo;
import io.github.pgatzka.shorty.render.domain.VisualStyle;
import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One entry of the upload history. */
@Entity
@Table(name = "upload_record")
public class UploadRecord {

    private static final int MAX_ERROR_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private PublishTrigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UploadStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "algorithm", nullable = false)
    private SortAlgorithm algorithm;

    @Enumerated(EnumType.STRING)
    @Column(name = "style", nullable = false)
    private VisualStyle style;

    @Column(name = "bar_count")
    private Integer barCount;

    @Column(name = "duration_seconds")
    private Double durationSeconds;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "video_file")
    private String videoFile;

    @Column(name = "youtube_video_id")
    private String youtubeVideoId;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    protected UploadRecord() {
        // for JPA
    }

    public UploadRecord(PublishTrigger trigger, SortAlgorithm algorithm, VisualStyle style, Instant createdAt) {
        this.trigger = trigger;
        this.algorithm = algorithm;
        this.style = style;
        this.createdAt = createdAt;
        this.status = UploadStatus.RENDERING;
    }

    /** Rendering finished; the upload is about to start. */
    public void rendered(RenderedVideo video, VideoMetadata metadata) {
        this.barCount = video.barCount();
        this.durationSeconds = video.durationSeconds();
        this.videoFile = video.fileName();
        this.title = metadata.title();
        this.description = metadata.description();
        this.status = UploadStatus.UPLOADING;
    }

    public void uploaded(String videoId, Instant at) {
        this.youtubeVideoId = videoId;
        this.uploadedAt = at;
        this.status = UploadStatus.UPLOADED;
        this.errorMessage = null;
    }

    public void failed(String message) {
        this.status = UploadStatus.FAILED;
        String text = message == null || message.isBlank() ? "Unknown error" : message;
        this.errorMessage = text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) : text;
    }

    /** The MP4 was removed by retention. */
    public void clearVideoFile() {
        this.videoFile = null;
    }

    /** Public link to the Short, or {@code null} if not uploaded. */
    public String url() {
        return youtubeVideoId == null ? null : "https://youtube.com/shorts/" + youtubeVideoId;
    }

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public PublishTrigger getTrigger() {
        return trigger;
    }

    public UploadStatus getStatus() {
        return status;
    }

    public SortAlgorithm getAlgorithm() {
        return algorithm;
    }

    public VisualStyle getStyle() {
        return style;
    }

    public Integer getBarCount() {
        return barCount;
    }

    public Double getDurationSeconds() {
        return durationSeconds;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getVideoFile() {
        return videoFile;
    }

    public String getYoutubeVideoId() {
        return youtubeVideoId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
