package io.github.pgatzka.shorty.ui.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import io.github.pgatzka.shorty.analytics.domain.VideoPerformance;
import io.github.pgatzka.shorty.analytics.service.AnalyticsService;
import io.github.pgatzka.shorty.publishing.domain.PublishTrigger;
import io.github.pgatzka.shorty.publishing.domain.UploadRecord;
import io.github.pgatzka.shorty.publishing.service.PublishingService;
import io.github.pgatzka.shorty.render.domain.VisualStyle;
import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import io.github.pgatzka.shorty.youtube.domain.YouTubeException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class HistoryAndAnalyticsViewTest extends ViewTestBase {

    @MockitoBean
    PublishingService publishingService;

    @MockitoBean
    AnalyticsService analyticsService;

    private static UploadRecord record() {
        return new UploadRecord(
                PublishTrigger.SCHEDULED, SortAlgorithm.BUBBLE, VisualStyle.CLASSIC_BARS, Instant.EPOCH);
    }

    @Test
    void historyListsUploads() {
        UploadRecord uploaded = record();
        uploaded.uploaded("vid", Instant.EPOCH);
        UploadRecord failed = record();
        failed.failed("YouTube access was revoked");
        when(publishingService.recentHistory(HistoryView.LIMIT)).thenReturn(List.of(uploaded, failed, record()));

        HistoryView view = navigate(HistoryView.class);
        assertThat(test(view.grid()).size()).isEqualTo(3);
        assertThat(test(view.grid()).getCellText(1, 10)).isEqualTo("YouTube access was revoked");

        test($(Button.class).withText("Refresh").single()).click();
        verify(publishingService, times(2)).recentHistory(HistoryView.LIMIT);
    }

    @Test
    void historyBadgesAndLinks() {
        UploadRecord uploaded = record();
        uploaded.uploaded("vid", Instant.EPOCH);
        UploadRecord failed = record();
        failed.failed("x");

        assertThat(badge(uploaded)).isEqualTo("badge success");
        assertThat(badge(failed)).isEqualTo("badge error");
        assertThat(badge(record())).isEqualTo("badge contrast");

        Component link = HistoryView.link(uploaded);
        assertThat(link).isInstanceOf(Anchor.class);
        assertThat(((Anchor) link).getHref()).isEqualTo("https://youtube.com/shorts/vid");
        assertThat(HistoryView.link(failed)).isInstanceOf(Span.class);
    }

    @Test
    void analyticsShowsTotals() {
        when(analyticsService.performance())
                .thenReturn(List.of(
                        new VideoPerformance("a", "A", "Bubble Sort", Instant.EPOCH, 1200, 30, 4, Instant.EPOCH),
                        new VideoPerformance("b", "B", "Quick Sort", Instant.EPOCH, 800, 10, 1, null)));
        when(analyticsService.refresh()).thenReturn(2);

        AnalyticsView view = navigate(AnalyticsView.class);
        assertThat(view.totals().getText()).isEqualTo("2 videos · 2,000 views · 40 likes · 5 comments");
        assertThat(test(view.grid()).size()).isEqualTo(2);

        test($(Button.class).withText("Fetch latest statistics").single()).click();
        verify(analyticsService).refresh();
        assertThat(notifications()).contains("Updated statistics for 2 videos");
    }

    @Test
    void analyticsShowsFetchErrors() {
        when(analyticsService.performance()).thenReturn(List.of());
        when(analyticsService.refresh()).thenThrow(new YouTubeException("YouTube is not connected"));

        navigate(AnalyticsView.class);
        test($(Button.class).withText("Fetch latest statistics").single()).click();

        assertThat(notifications()).contains("YouTube is not connected");
    }

    @Test
    void formats() {
        assertThat(Formats.dateTime(null)).isEmpty();
        assertThat(Formats.seconds(null)).isEmpty();
        assertThat(Formats.seconds(61.4)).isEqualTo("61 s");
        assertThat(Formats.number(1234567)).isEqualTo("1,234,567");
    }

    private static String badge(UploadRecord upload) {
        return HistoryView.statusBadge(upload).getElement().getAttribute("theme");
    }
}
