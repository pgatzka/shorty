package io.github.pgatzka.shorty.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.pgatzka.shorty.analytics.domain.AnalyticsTotals;
import io.github.pgatzka.shorty.analytics.domain.StatsSnapshot;
import io.github.pgatzka.shorty.analytics.domain.VideoPerformance;
import io.github.pgatzka.shorty.analytics.repository.StatsSnapshotRepository;
import io.github.pgatzka.shorty.publishing.domain.PublishTrigger;
import io.github.pgatzka.shorty.publishing.domain.UploadRecord;
import io.github.pgatzka.shorty.publishing.service.PublishingService;
import io.github.pgatzka.shorty.render.domain.VisualStyle;
import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import io.github.pgatzka.shorty.youtube.domain.VideoStatistics;
import io.github.pgatzka.shorty.youtube.domain.YouTubeException;
import io.github.pgatzka.shorty.youtube.service.YouTubeService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class AnalyticsServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-27T12:00:00Z");

    private final PublishingService publishing = mock(PublishingService.class);
    private final YouTubeService youTube = mock(YouTubeService.class);
    private final StatsSnapshotRepository repository = mock(StatsSnapshotRepository.class);
    private final AnalyticsService service =
            new AnalyticsService(publishing, youTube, repository, Clock.fixed(NOW, ZoneOffset.UTC));

    private static UploadRecord uploaded(String videoId, String title) {
        UploadRecord upload =
                new UploadRecord(PublishTrigger.MANUAL, SortAlgorithm.MERGE, VisualStyle.CLASSIC_BARS, NOW);
        upload.uploaded(videoId, NOW);
        ReflectionTestUtils.setField(upload, "title", title);
        return upload;
    }

    @Test
    @SuppressWarnings("unchecked")
    void refreshStoresSnapshots() {
        when(publishing.uploadedVideos(AnalyticsService.TRACKED_VIDEOS)).thenReturn(List.of(uploaded("a", "A")));
        when(youTube.statistics(List.of("a"))).thenReturn(List.of(new VideoStatistics("a", 5, 2, 1)));

        assertThat(service.refresh()).isEqualTo(1);

        ArgumentCaptor<List<StatsSnapshot>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        StatsSnapshot snapshot = saved.getValue().getFirst();
        assertThat(snapshot.getVideoId()).isEqualTo("a");
        assertThat(snapshot.getTakenAt()).isEqualTo(NOW);
        assertThat(snapshot.getViews()).isEqualTo(5);
        assertThat(snapshot.getLikes()).isEqualTo(2);
        assertThat(snapshot.getComments()).isEqualTo(1);
    }

    @Test
    void refreshWithoutUploadsSkipsYouTube() {
        when(publishing.uploadedVideos(AnalyticsService.TRACKED_VIDEOS)).thenReturn(List.of());
        assertThat(service.refresh()).isZero();
        verifyNoInteractions(youTube);
    }

    @Test
    void scheduledRefreshSwallowsYouTubeErrors() {
        when(publishing.uploadedVideos(AnalyticsService.TRACKED_VIDEOS)).thenReturn(List.of(uploaded("a", "A")));
        when(youTube.statistics(any())).thenThrow(new YouTubeException("not connected"));
        assertThatCode(service::scheduledRefresh).doesNotThrowAnyException();

        doReturn(List.of()).when(youTube).statistics(any());
        assertThatCode(service::scheduledRefresh).doesNotThrowAnyException();
    }

    @Test
    void performanceCombinesUploadsAndLatestSnapshots() {
        when(publishing.uploadedVideos(AnalyticsService.TRACKED_VIDEOS))
                .thenReturn(List.of(uploaded("a", "A"), uploaded("b", "B")));
        when(repository.findLatest(anyList())).thenReturn(List.of(new StatsSnapshot("a", NOW, 10, 3, 1)));

        List<VideoPerformance> performance = service.performance();

        assertThat(performance)
                .containsExactly(
                        new VideoPerformance("a", "A", "Merge Sort", NOW, 10, 3, 1, NOW),
                        new VideoPerformance("b", "B", "Merge Sort", NOW, 0, 0, 0, null));
        assertThat(performance.getFirst().url()).isEqualTo("https://youtube.com/shorts/a");
        assertThat(AnalyticsTotals.of(performance)).isEqualTo(new AnalyticsTotals(2, 10, 3, 1));
    }

    @Test
    void performanceWithoutUploadsIsEmpty() {
        when(publishing.uploadedVideos(AnalyticsService.TRACKED_VIDEOS)).thenReturn(List.of());
        assertThat(service.performance()).isEmpty();
    }
}
