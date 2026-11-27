package io.github.pgatzka.shorty.analytics.service;

import io.github.pgatzka.shorty.analytics.domain.StatsSnapshot;
import io.github.pgatzka.shorty.analytics.domain.VideoPerformance;
import io.github.pgatzka.shorty.analytics.repository.StatsSnapshotRepository;
import io.github.pgatzka.shorty.publishing.domain.UploadRecord;
import io.github.pgatzka.shorty.publishing.service.PublishingService;
import io.github.pgatzka.shorty.youtube.domain.VideoStatistics;
import io.github.pgatzka.shorty.youtube.domain.YouTubeException;
import io.github.pgatzka.shorty.youtube.service.YouTubeService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Collects statistics snapshots and serves the latest numbers per video. */
@Service
public class AnalyticsService {

    private static final Logger LOG = LoggerFactory.getLogger(AnalyticsService.class);
    static final int TRACKED_VIDEOS = 200;

    private final PublishingService publishingService;
    private final YouTubeService youTubeService;
    private final StatsSnapshotRepository repository;
    private final Clock clock;

    public AnalyticsService(
            PublishingService publishingService,
            YouTubeService youTubeService,
            StatsSnapshotRepository repository,
            Clock clock) {
        this.publishingService = publishingService;
        this.youTubeService = youTubeService;
        this.repository = repository;
        this.clock = clock;
    }

    /** Scheduled refresh; failures (for example no connection yet) are logged, not thrown. */
    @Scheduled(cron = "${shorty.analytics.cron:0 0 */6 * * *}")
    public void scheduledRefresh() {
        try {
            int count = refresh();
            LOG.info("Stored statistics for {} videos", count);
        } catch (YouTubeException e) {
            LOG.warn("Statistics refresh skipped: {}", e.getMessage());
        }
    }

    /** Reads current statistics of the uploaded videos and stores a snapshot; returns the count. */
    @Transactional
    public int refresh() {
        List<String> ids = uploadedIds();
        if (ids.isEmpty()) {
            return 0;
        }
        Instant now = clock.instant();
        List<VideoStatistics> statistics = youTubeService.statistics(ids);
        repository.saveAll(statistics.stream()
                .map(s -> new StatsSnapshot(s.videoId(), now, s.views(), s.likes(), s.comments()))
                .toList());
        return statistics.size();
    }

    /** Latest numbers per uploaded video, newest upload first. */
    @Transactional(readOnly = true)
    public List<VideoPerformance> performance() {
        List<UploadRecord> uploads = publishingService.uploadedVideos(TRACKED_VIDEOS);
        if (uploads.isEmpty()) {
            return List.of();
        }
        Map<String, StatsSnapshot> latest =
                repository
                        .findLatest(uploads.stream()
                                .map(UploadRecord::getYoutubeVideoId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(StatsSnapshot::getVideoId, Function.identity(), (a, b) -> a));
        return uploads.stream()
                .map(upload -> {
                    StatsSnapshot snapshot = latest.get(upload.getYoutubeVideoId());
                    return new VideoPerformance(
                            upload.getYoutubeVideoId(),
                            upload.getTitle(),
                            upload.getAlgorithm().displayName(),
                            upload.getUploadedAt(),
                            snapshot == null ? 0 : snapshot.getViews(),
                            snapshot == null ? 0 : snapshot.getLikes(),
                            snapshot == null ? 0 : snapshot.getComments(),
                            snapshot == null ? null : snapshot.getTakenAt());
                })
                .toList();
    }

    private List<String> uploadedIds() {
        return publishingService.uploadedVideos(TRACKED_VIDEOS).stream()
                .map(UploadRecord::getYoutubeVideoId)
                .toList();
    }
}
