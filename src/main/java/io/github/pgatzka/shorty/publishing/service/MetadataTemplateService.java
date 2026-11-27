package io.github.pgatzka.shorty.publishing.service;

import io.github.pgatzka.shorty.publishing.domain.VideoMetadata;
import io.github.pgatzka.shorty.render.domain.RenderedVideo;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Fills the rotating title/description templates. Placeholders: {@code {algorithm}}, {@code
 * {bars}}, {@code {bigO}}, {@code {comparisons}}, {@code {swaps}}, {@code {accesses}}, {@code
 * {seconds}}, {@code {handle}}.
 */
@Service
public class MetadataTemplateService {

    /** YouTube's title limit. */
    static final int MAX_TITLE_LENGTH = 100;

    private final PublishingProperties properties;

    public MetadataTemplateService(PublishingProperties properties) {
        this.properties = properties;
    }

    public VideoMetadata build(RenderedVideo video, long sequence, String channelHandle) {
        Map<String, String> values = Map.of(
                "{algorithm}", video.algorithm().displayName(),
                "{bars}", Integer.toString(video.barCount()),
                "{bigO}", video.algorithm().bigO(),
                "{comparisons}", number(video.comparisons()),
                "{swaps}", number(video.swaps()),
                "{accesses}", number(video.accesses()),
                "{seconds}", Long.toString(Math.round(video.durationSeconds())),
                "{handle}", channelHandle == null || channelHandle.isBlank() ? "this channel" : channelHandle);
        String title = fill(pick(properties.titleTemplates(), sequence), values);
        if (title.length() > MAX_TITLE_LENGTH) {
            title = title.substring(0, MAX_TITLE_LENGTH).strip();
        }
        String description = fill(pick(properties.descriptionTemplates(), sequence), values);
        return new VideoMetadata(title, description, tags(video));
    }

    private List<String> tags(RenderedVideo video) {
        Set<String> tags = new LinkedHashSet<>(properties.tags());
        tags.add(video.algorithm().displayName().toLowerCase(Locale.ROOT));
        return new ArrayList<>(tags);
    }

    private static String pick(List<String> templates, long sequence) {
        return templates.get((int) Math.floorMod(sequence, (long) templates.size()));
    }

    private static String fill(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result.strip();
    }

    private static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
