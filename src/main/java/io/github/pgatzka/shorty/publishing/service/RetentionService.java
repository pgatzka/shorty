package io.github.pgatzka.shorty.publishing.service;

import io.github.pgatzka.shorty.publishing.domain.UploadRecord;
import io.github.pgatzka.shorty.publishing.repository.UploadRecordRepository;
import io.github.pgatzka.shorty.render.service.VideoRenderer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Keeps only the newest N MP4 files in the output directory. */
@Service
public class RetentionService {

    private static final Logger LOG = LoggerFactory.getLogger(RetentionService.class);

    private final VideoRenderer renderer;
    private final UploadRecordRepository repository;
    private final PublishingProperties properties;

    public RetentionService(
            VideoRenderer renderer, UploadRecordRepository repository, PublishingProperties properties) {
        this.renderer = renderer;
        this.repository = repository;
        this.properties = properties;
    }

    /** Deletes older files beyond the retention count; returns how many were deleted. */
    public synchronized int enforce() {
        Path directory = renderer.outputDirectory();
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        List<Path> videos;
        try (Stream<Path> files = Files.list(directory)) {
            videos = files.filter(file -> file.getFileName().toString().endsWith(".mp4"))
                    .sorted(Comparator.comparing(RetentionService::modified).reversed())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int deleted = 0;
        for (Path file : videos.stream().skip(properties.retentionCount()).toList()) {
            if (delete(file)) {
                deleted++;
                forget(file.getFileName().toString());
            }
        }
        return deleted;
    }

    private void forget(String fileName) {
        for (UploadRecord upload : repository.findByVideoFile(fileName)) {
            upload.clearVideoFile();
            repository.save(upload);
        }
    }

    private static boolean delete(Path file) {
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.warn("Could not delete {}: {}", file, e.getMessage());
            return false;
        }
    }

    private static FileTime modified(Path file) {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }
}
