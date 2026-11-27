package io.github.pgatzka.shorty.publishing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.pgatzka.shorty.publishing.domain.ChannelSettings;
import io.github.pgatzka.shorty.publishing.domain.PublishTrigger;
import io.github.pgatzka.shorty.publishing.domain.UploadRecord;
import io.github.pgatzka.shorty.publishing.domain.UploadStatus;
import io.github.pgatzka.shorty.publishing.repository.UploadRecordRepository;
import io.github.pgatzka.shorty.render.domain.RenderRequest;
import io.github.pgatzka.shorty.render.domain.RenderedVideo;
import io.github.pgatzka.shorty.render.domain.VisualStyle;
import io.github.pgatzka.shorty.render.service.RenderException;
import io.github.pgatzka.shorty.render.service.VideoRenderer;
import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import io.github.pgatzka.shorty.youtube.domain.UploadRequest;
import io.github.pgatzka.shorty.youtube.domain.UploadResult;
import io.github.pgatzka.shorty.youtube.domain.YouTubeException;
import io.github.pgatzka.shorty.youtube.service.YouTubeService;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class PublishingServiceTest {

    static final Instant NOW = Instant.parse("2026-11-27T12:00:00Z");

    private final VideoRenderer renderer = mock(VideoRenderer.class);
    private final YouTubeService youTube = mock(YouTubeService.class);
    private final UploadRecordRepository repository = mock(UploadRecordRepository.class);
    private final ChannelSettingsService channelSettings = mock(ChannelSettingsService.class);
    private final RetentionService retention = mock(RetentionService.class);
    private final List<Runnable> queued = new ArrayList<>();
    private final List<UploadRecord> saved = new ArrayList<>();
    private PublishingService service;

    @BeforeEach
    void setUp() {
        AtomicLong ids = new AtomicLong();
        when(repository.save(any())).thenAnswer(invocation -> {
            UploadRecord upload = invocation.getArgument(0);
            if (upload.getId() == null) {
                ReflectionTestUtils.setField(upload, "id", ids.incrementAndGet());
            }
            saved.add(upload);
            when(repository.findById(upload.getId())).thenReturn(Optional.of(upload));
            return upload;
        });
        when(channelSettings.current()).thenReturn(new ChannelSettings("@sorty", "unlisted"));
        service = new PublishingService(
                renderer,
                youTube,
                repository,
                new MetadataTemplateService(PropertiesFixture.properties()),
                channelSettings,
                retention,
                queued::add,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static RenderedVideo video(RenderRequest request) {
        return new RenderedVideo(
                Path.of("videos/a.mp4"), request.algorithm(), request.style(), 42, 61.5, 900, 400, 2600);
    }

    @Test
    void publishRendersUploadsAndRecords() {
        when(repository.findFirstByOrderByIdDesc()).thenReturn(Optional.empty());
        when(renderer.render(any())).thenAnswer(invocation -> video(invocation.getArgument(0)));
        when(youTube.upload(any())).thenReturn(new UploadResult("vid1"));

        UploadRecord upload = service.startPublish(PublishTrigger.MANUAL);
        assertThat(upload.getStatus()).isEqualTo(UploadStatus.RENDERING);
        assertThat(upload.getAlgorithm()).isEqualTo(SortAlgorithm.BUBBLE);
        assertThat(upload.getStyle()).isEqualTo(VisualStyle.CLASSIC_BARS);
        assertThat(upload.getCreatedAt()).isEqualTo(NOW);

        queued.forEach(Runnable::run);

        assertThat(upload.getStatus()).isEqualTo(UploadStatus.UPLOADED);
        assertThat(upload.getYoutubeVideoId()).isEqualTo("vid1");
        assertThat(upload.url()).isEqualTo("https://youtube.com/shorts/vid1");
        assertThat(upload.getUploadedAt()).isEqualTo(NOW);
        assertThat(upload.getBarCount()).isEqualTo(42);
        assertThat(upload.getDurationSeconds()).isEqualTo(61.5);
        assertThat(upload.getVideoFile()).isEqualTo("a.mp4");
        assertThat(upload.getTitle()).contains("Bubble Sort");
        assertThat(upload.getDescription()).contains("@sorty");
        assertThat(upload.getTrigger()).isEqualTo(PublishTrigger.MANUAL);

        ArgumentCaptor<UploadRequest> request = ArgumentCaptor.forClass(UploadRequest.class);
        verify(youTube).upload(request.capture());
        assertThat(request.getValue().privacyStatus()).isEqualTo("unlisted");
        assertThat(request.getValue().tags()).contains("sorting", "bubble sort");
        ArgumentCaptor<RenderRequest> render = ArgumentCaptor.forClass(RenderRequest.class);
        verify(renderer).render(render.capture());
        assertThat(render.getValue().channelHandle()).isEqualTo("@sorty");
        verify(retention).enforce();
    }

    @Test
    void rotatesAfterTheLastUpload() {
        UploadRecord last =
                new UploadRecord(PublishTrigger.SCHEDULED, SortAlgorithm.MERGE, VisualStyle.SCATTER_DOTS, NOW);
        when(repository.findFirstByOrderByIdDesc()).thenReturn(Optional.of(last));

        UploadRecord upload = service.startPublish(PublishTrigger.SCHEDULED);

        assertThat(upload.getAlgorithm()).isEqualTo(SortAlgorithm.INSERTION);
        assertThat(upload.getStyle()).isEqualTo(VisualStyle.MIRRORED_BARS);
    }

    @Test
    void failedUploadIsRecorded() {
        when(repository.findFirstByOrderByIdDesc()).thenReturn(Optional.empty());
        when(renderer.render(any())).thenAnswer(invocation -> video(invocation.getArgument(0)));
        when(youTube.upload(any())).thenThrow(new YouTubeException("YouTube access was revoked"));

        UploadRecord upload = service.startPublish(PublishTrigger.MANUAL);
        queued.forEach(Runnable::run);

        assertThat(upload.getStatus()).isEqualTo(UploadStatus.FAILED);
        assertThat(upload.getErrorMessage()).isEqualTo("YouTube access was revoked");
        assertThat(service.isBusy()).isFalse();
    }

    @Test
    void failedRenderIsRecordedAndRetentionErrorsAreSwallowed() {
        when(repository.findFirstByOrderByIdDesc()).thenReturn(Optional.empty());
        when(renderer.render(any())).thenThrow(new RenderException("ffmpeg missing"));
        doThrow(new IllegalStateException("disk")).when(retention).enforce();

        UploadRecord upload = service.startPublish(PublishTrigger.MANUAL);
        queued.forEach(Runnable::run);

        assertThat(upload.getStatus()).isEqualTo(UploadStatus.FAILED);
        assertThat(upload.getErrorMessage()).isEqualTo("ffmpeg missing");
    }

    @Test
    void exceptionWithoutMessageUsesItsType() {
        when(repository.findFirstByOrderByIdDesc()).thenReturn(Optional.empty());
        when(renderer.render(any())).thenThrow(new IllegalStateException());

        UploadRecord upload = service.startPublish(PublishTrigger.MANUAL);
        queued.forEach(Runnable::run);

        assertThat(upload.getErrorMessage()).isEqualTo("IllegalStateException");
    }

    @Test
    void previewRendersWithoutUploading() throws ExecutionException, InterruptedException {
        when(renderer.render(any())).thenAnswer(invocation -> video(invocation.getArgument(0)));

        CompletableFuture<RenderedVideo> future = service.renderPreview(SortAlgorithm.QUICK, VisualStyle.RAINBOW_BARS);
        assertThat(future).isNotDone();
        queued.forEach(Runnable::run);

        assertThat(future.get().algorithm()).isEqualTo(SortAlgorithm.QUICK);
        assertThat(future.get().style()).isEqualTo(VisualStyle.RAINBOW_BARS);
        verify(retention).enforce();
        verify(youTube, never()).upload(any());
    }

    @Test
    void busyWhileAJobRuns() {
        when(renderer.render(any())).thenAnswer(invocation -> {
            assertThat(service.isBusy()).isTrue();
            return video(invocation.getArgument(0));
        });
        service.renderPreview(SortAlgorithm.BUBBLE, VisualStyle.CLASSIC_BARS);
        queued.forEach(Runnable::run);
        assertThat(service.isBusy()).isFalse();
    }

    @Test
    void interruptedJobsAreFailedOnStartup() {
        UploadRecord stuck =
                new UploadRecord(PublishTrigger.MANUAL, SortAlgorithm.BUBBLE, VisualStyle.CLASSIC_BARS, NOW);
        when(repository.findByStatusIn(List.of(UploadStatus.RENDERING, UploadStatus.UPLOADING)))
                .thenReturn(List.of(stuck));

        service.failInterruptedJobs();

        assertThat(stuck.getStatus()).isEqualTo(UploadStatus.FAILED);
        assertThat(stuck.getErrorMessage()).contains("restart");
    }

    @Test
    void historyQueries() {
        service.recentHistory(10);
        service.uploadedVideos(5);
        verify(repository).findAllByOrderByIdDesc(Pageable.ofSize(10));
        verify(repository).findByStatusOrderByIdDesc(UploadStatus.UPLOADED, Pageable.ofSize(5));
    }
}
