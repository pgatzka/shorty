package io.github.pgatzka.shorty.publishing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.pgatzka.shorty.publishing.domain.PublishTrigger;
import io.github.pgatzka.shorty.publishing.domain.UploadRecord;
import io.github.pgatzka.shorty.publishing.domain.VideoMetadata;
import io.github.pgatzka.shorty.publishing.repository.UploadRecordRepository;
import io.github.pgatzka.shorty.render.domain.RenderedVideo;
import io.github.pgatzka.shorty.render.domain.VisualStyle;
import io.github.pgatzka.shorty.render.service.VideoRenderer;
import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RetentionServiceTest {

    @TempDir
    Path dir;

    private final VideoRenderer renderer = mock(VideoRenderer.class);
    private final UploadRecordRepository repository = mock(UploadRecordRepository.class);

    @Test
    void keepsOnlyTheNewestFiles() throws Exception {
        when(renderer.outputDirectory()).thenReturn(dir);
        for (int i = 0; i < 5; i++) {
            Path file = Files.writeString(dir.resolve("v" + i + ".mp4"), "x");
            Files.setLastModifiedTime(file, FileTime.from(Instant.ofEpochSecond(1000L + i)));
        }
        Files.writeString(dir.resolve("notes.txt"), "keep");
        UploadRecord upload =
                new UploadRecord(PublishTrigger.MANUAL, SortAlgorithm.BUBBLE, VisualStyle.CLASSIC_BARS, Instant.EPOCH);
        upload.rendered(
                new RenderedVideo(dir.resolve("v0.mp4"), SortAlgorithm.BUBBLE, VisualStyle.CLASSIC_BARS, 1, 1, 1, 1, 1),
                new VideoMetadata("t", "d", List.of()));
        when(repository.findByVideoFile("v0.mp4")).thenReturn(List.of(upload));

        int deleted = new RetentionService(renderer, repository, PropertiesFixture.properties()).enforce();

        assertThat(deleted).isEqualTo(2);
        assertThat(dir.resolve("v4.mp4")).exists();
        assertThat(dir.resolve("v2.mp4")).exists();
        assertThat(dir.resolve("v1.mp4")).doesNotExist();
        assertThat(dir.resolve("v0.mp4")).doesNotExist();
        assertThat(dir.resolve("notes.txt")).exists();
        assertThat(upload.getVideoFile()).isNull();
        verify(repository).save(upload);
    }

    @Test
    void missingDirectoryIsFine() {
        when(renderer.outputDirectory()).thenReturn(dir.resolve("missing"));
        assertThat(new RetentionService(renderer, repository, PropertiesFixture.properties()).enforce())
                .isZero();
    }
}
