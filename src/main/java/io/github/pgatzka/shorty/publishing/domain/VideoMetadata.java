package io.github.pgatzka.shorty.publishing.domain;

import java.util.List;

/** Title, description and tags of an upload. */
public record VideoMetadata(String title, String description, List<String> tags) {

    public VideoMetadata {
        tags = List.copyOf(tags);
    }
}
