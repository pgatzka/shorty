package io.github.pgatzka.shorty.publishing.service;

import io.github.pgatzka.shorty.publishing.domain.ChannelSettings;
import io.github.pgatzka.shorty.publishing.domain.PublishTrigger;
import io.github.pgatzka.shorty.publishing.domain.UploadRecord;
import io.github.pgatzka.shorty.publishing.domain.UploadStatus;
import io.github.pgatzka.shorty.publishing.domain.VideoMetadata;
import io.github.pgatzka.shorty.publishing.repository.UploadRecordRepository;
import io.github.pgatzka.shorty.render.domain.RenderRequest;
import io.github.pgatzka.shorty.render.domain.RenderedVideo;
import io.github.pgatzka.shorty.render.domain.VisualStyle;
import io.github.pgatzka.shorty.render.service.VideoRenderer;
import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import io.github.pgatzka.shorty.youtube.domain.UploadRequest;
import io.github.pgatzka.shorty.youtube.domain.UploadResult;
import io.github.pgatzka.shorty.youtube.service.YouTubeService;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** Renders and uploads videos on the single render worker, recording everything in the history. */
@Service
public class PublishingService {

    private static final Logger LOG = LoggerFactory.getLogger(PublishingService.class);

    private final VideoRenderer renderer;
    private final YouTubeService youTube;
    private final UploadRecordRepository repository;
    private final MetadataTemplateService templates;
    private final ChannelSettingsService channelSettings;
    private final RetentionService retention;
    private final Executor executor;
    private final Clock clock;
    private final AtomicInteger activeJobs = new AtomicInteger();

    public PublishingService(
            VideoRenderer renderer,
            YouTubeService youTube,
            UploadRecordRepository repository,
            MetadataTemplateService templates,
            ChannelSettingsService channelSettings,
            RetentionService retention,
            @Qualifier(PublishingConfig.RENDER_EXECUTOR) Executor executor,
            Clock clock) {
        this.renderer = renderer;
        this.youTube = youTube;
        this.repository = repository;
        this.templates = templates;
        this.channelSettings = channelSettings;
        this.retention = retention;
        this.executor = executor;
        this.clock = clock;
    }

    /** Records left in RENDERING/UPLOADING by a restart can never finish; mark them failed. */
    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedJobs() {
        for (UploadRecord upload : repository.findByStatusIn(List.of(UploadStatus.RENDERING, UploadStatus.UPLOADING))) {
            upload.failed("Interrupted by an application restart");
            repository.save(upload);
        }
    }

    /** Renders a test video without uploading it. */
    public CompletableFuture<RenderedVideo> renderPreview(SortAlgorithm algorithm, VisualStyle style) {
        return CompletableFuture.supplyAsync(
                () -> track(() -> {
                    RenderedVideo video = renderer.render(new RenderRequest(
                            algorithm,
                            style,
                            ThreadLocalRandom.current().nextLong(),
                            channelSettings.current().getChannelHandle()));
                    enforceRetention();
                    return video;
                }),
                executor);
    }

    /** Queues a render + upload with the next algorithm and style in rotation. */
    public UploadRecord startPublish(PublishTrigger trigger) {
        UploadRecord upload;
        synchronized (this) {
            UploadRecord last = repository.findFirstByOrderByIdDesc().orElse(null);
            SortAlgorithm algorithm =
                    last == null ? SortAlgorithm.BUBBLE : last.getAlgorithm().next();
            VisualStyle style =
                    last == null ? VisualStyle.CLASSIC_BARS : last.getStyle().next();
            upload = repository.save(new UploadRecord(trigger, algorithm, style, clock.instant()));
        }
        long id = upload.getId();
        executor.execute(() -> track(() -> {
            publish(id);
            return null;
        }));
        return upload;
    }

    /** A render or upload is running or queued. */
    public boolean isBusy() {
        return activeJobs.get() > 0;
    }

    public List<UploadRecord> recentHistory(int limit) {
        return repository.findAllByOrderByIdDesc(PageRequest.of(0, limit));
    }

    public List<UploadRecord> uploadedVideos(int limit) {
        return repository.findByStatusOrderByIdDesc(UploadStatus.UPLOADED, PageRequest.of(0, limit));
    }

    /** Error boundary of a job: any failure ends up in the history instead of killing the worker. */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void publish(long id) {
        UploadRecord upload = repository.findById(id).orElseThrow();
        try {
            ChannelSettings settings = channelSettings.current();
            RenderedVideo video = renderer.render(new RenderRequest(
                    upload.getAlgorithm(),
                    upload.getStyle(),
                    ThreadLocalRandom.current().nextLong(),
                    settings.getChannelHandle()));
            VideoMetadata metadata = templates.build(video, id, settings.getChannelHandle());
            upload.rendered(video, metadata);
            upload = repository.save(upload);
            UploadResult result = youTube.upload(new UploadRequest(
                    video.file(),
                    metadata.title(),
                    metadata.description(),
                    metadata.tags(),
                    settings.getPrivacyStatus()));
            upload.uploaded(result.videoId(), clock.instant());
            repository.save(upload);
            LOG.info("Uploaded {} as {}", video.fileName(), result.url());
        } catch (RuntimeException e) {
            LOG.warn("Publishing #{} failed", id, e);
            upload.failed(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            repository.save(upload);
        } finally {
            enforceRetention();
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void enforceRetention() {
        try {
            retention.enforce();
        } catch (RuntimeException e) {
            LOG.warn("Retention failed", e);
        }
    }

    private <T> T track(Supplier<T> job) {
        activeJobs.incrementAndGet();
        try {
            return job.get();
        } finally {
            activeJobs.decrementAndGet();
        }
    }
}
