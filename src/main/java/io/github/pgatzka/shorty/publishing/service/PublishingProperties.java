package io.github.pgatzka.shorty.publishing.service;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Publishing settings ({@code shorty.publishing.*}).
 *
 * @param retentionCount number of newest MP4 files to keep
 * @param titleTemplates rotating title templates
 * @param descriptionTemplates rotating description templates
 * @param tags tags added to every upload
 */
@ConfigurationProperties("shorty.publishing")
public record PublishingProperties(
        @DefaultValue("20") int retentionCount,

        @DefaultValue("{algorithm} sorting {bars} bars #shorts")
        List<String> titleTemplates,

        @DefaultValue("{algorithm} sorting {bars} random values. {comparisons} comparisons, {swaps} swaps.")
        List<String> descriptionTemplates,

        @DefaultValue("sorting,algorithm,shorts") List<String> tags) {

    public PublishingProperties {
        if (retentionCount < 1) {
            throw new IllegalArgumentException("retentionCount must be at least 1");
        }
        if (titleTemplates.isEmpty() || descriptionTemplates.isEmpty()) {
            throw new IllegalArgumentException("At least one title and one description template is required");
        }
        titleTemplates = List.copyOf(titleTemplates);
        descriptionTemplates = List.copyOf(descriptionTemplates);
        tags = List.copyOf(tags);
    }
}
