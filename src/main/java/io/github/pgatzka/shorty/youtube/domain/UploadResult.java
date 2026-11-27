package io.github.pgatzka.shorty.youtube.domain;

import java.util.Objects;

/** A successful upload. */
public record UploadResult(String videoId) {

    public UploadResult {
        Objects.requireNonNull(videoId, "videoId");
    }

    public String url() {
        return shortsUrl(videoId);
    }

    public static String shortsUrl(String videoId) {
        return "https://youtube.com/shorts/" + videoId;
    }
}
