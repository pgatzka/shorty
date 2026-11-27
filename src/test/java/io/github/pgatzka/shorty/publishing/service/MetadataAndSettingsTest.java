package io.github.pgatzka.shorty.publishing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.pgatzka.shorty.publishing.domain.ChannelSettings;
import io.github.pgatzka.shorty.publishing.domain.PublishTrigger;
import io.github.pgatzka.shorty.publishing.domain.UploadRecord;
import io.github.pgatzka.shorty.publishing.domain.UploadStatus;
import io.github.pgatzka.shorty.publishing.domain.VideoMetadata;
import io.github.pgatzka.shorty.publishing.repository.ChannelSettingsRepository;
import io.github.pgatzka.shorty.render.domain.RenderedVideo;
import io.github.pgatzka.shorty.render.domain.VisualStyle;
import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MetadataAndSettingsTest {

    private static final RenderedVideo VIDEO = new RenderedVideo(
            Path.of("x.mp4"), SortAlgorithm.QUICK, VisualStyle.CLASSIC_BARS, 150, 58.4, 1234, 567, 8910);

    private final MetadataTemplateService templates = new MetadataTemplateService(PropertiesFixture.properties());

    @Test
    void fillsAndRotatesTemplates() {
        VideoMetadata first = templates.build(VIDEO, 0, "@me");
        VideoMetadata second = templates.build(VIDEO, 1, "");

        assertThat(first.title()).isEqualTo("Quick Sort sorting 150 bars #shorts");
        assertThat(second.title()).isEqualTo("Quick Sort in 58s, O(n log n)");
        assertThat(first.description()).isEqualTo("1,234 comparisons, 567 swaps, 8,910 accesses. Follow @me.");
        assertThat(second.description()).endsWith("Follow this channel.");
        assertThat(templates.build(VIDEO, 2, null).title()).isEqualTo(first.title());
        assertThat(first.tags()).containsExactly("sorting", "shorts", "quick sort");
    }

    @Test
    void titlesAreCutToYouTubesLimit() {
        PublishingProperties longTitle = new PublishingProperties(1, List.of("x".repeat(150)), List.of("d"), List.of());
        assertThat(new MetadataTemplateService(longTitle).build(VIDEO, 0, "").title())
                .hasSize(100);
    }

    @Test
    void propertiesAreValidated() {
        assertThatThrownBy(() -> new PublishingProperties(0, List.of("t"), List.of("d"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublishingProperties(1, List.of(), List.of("d"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublishingProperties(1, List.of("t"), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void channelSettingsNormalize() {
        ChannelSettings settings = new ChannelSettings(" mychannel ", "Unlisted");
        assertThat(settings.getChannelHandle()).isEqualTo("@mychannel");
        assertThat(settings.getPrivacyStatus()).isEqualTo("unlisted");
        assertThat(settings.getId()).isEqualTo(1);

        settings.update("@other", "private");
        assertThat(settings.getChannelHandle()).isEqualTo("@other");
        settings.update(null, "public");
        assertThat(settings.getChannelHandle()).isEmpty();

        assertThatThrownBy(() -> settings.update("x", "secret")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings.update("x", null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ChannelSettings.defaults().getPrivacyStatus()).isEqualTo("public");
    }

    @Test
    void channelSettingsService() {
        ChannelSettingsRepository repository = mock(ChannelSettingsRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ChannelSettingsService service = new ChannelSettingsService(repository);

        when(repository.findById(1)).thenReturn(Optional.empty());
        assertThat(service.current().getChannelHandle()).isEmpty();
        assertThat(service.update("abc", "public").getChannelHandle()).isEqualTo("@abc");

        ChannelSettings stored = new ChannelSettings("@x", "private");
        when(repository.findById(1)).thenReturn(Optional.of(stored));
        assertThat(service.current()).isSameAs(stored);
        assertThat(service.update("@y", "public")).isSameAs(stored);
        assertThat(stored.getChannelHandle()).isEqualTo("@y");
    }

    @Test
    void uploadRecordLifecycle() {
        UploadRecord upload =
                new UploadRecord(PublishTrigger.MANUAL, SortAlgorithm.QUICK, VisualStyle.CLASSIC_BARS, Instant.EPOCH);
        assertThat(upload.url()).isNull();
        upload.rendered(VIDEO, new VideoMetadata("t", "d", List.of()));
        assertThat(upload.getStatus()).isEqualTo(UploadStatus.UPLOADING);
        upload.failed("");
        assertThat(upload.getErrorMessage()).isEqualTo("Unknown error");
        upload.failed(null);
        assertThat(upload.getErrorMessage()).isEqualTo("Unknown error");
        upload.failed("e".repeat(3000));
        assertThat(upload.getErrorMessage()).hasSize(2000);
        upload.clearVideoFile();
        assertThat(upload.getVideoFile()).isNull();
        upload.uploaded("v", Instant.EPOCH);
        assertThat(upload.getErrorMessage()).isNull();
    }
}
