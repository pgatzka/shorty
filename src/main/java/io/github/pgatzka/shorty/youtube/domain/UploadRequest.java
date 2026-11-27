package io.github.pgatzka.shorty.youtube.domain;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * A video to upload.
 *
 * @param file MP4 file
 * @param title video title (max. 100 characters)
 * @param description video description
 * @param tags tags
 * @param privacyStatus {@code public}, {@code unlisted} or {@code private}
 */
public record UploadRequest(Path file, String title, String description, List<String> tags, String privacyStatus) {

    public UploadRequest {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(title, "title");
        description = description == null ? "" : description;
        tags = tags == null ? List.of() : List.copyOf(tags);
        privacyStatus = privacyStatus == null ? "public" : privacyStatus;
    }
}
