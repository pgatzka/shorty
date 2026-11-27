package io.github.pgatzka.shorty.ui.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.github.pgatzka.shorty.analytics.domain.AnalyticsTotals;
import io.github.pgatzka.shorty.analytics.domain.VideoPerformance;
import io.github.pgatzka.shorty.analytics.service.AnalyticsService;
import io.github.pgatzka.shorty.youtube.domain.YouTubeException;
import java.util.List;

/** Views, likes and comments of the uploaded Shorts. */
@Route(value = "analytics", layout = MainLayout.class)
@PageTitle("Analytics | Shorty")
public class AnalyticsView extends VerticalLayout {

    private static final long serialVersionUID = 1L;

    private final transient AnalyticsService analyticsService;
    private final Span totals = new Span();
    private final Grid<VideoPerformance> grid = new Grid<>(VideoPerformance.class, false);

    public AnalyticsView(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
        setSizeFull();

        grid.addColumn(VideoPerformance::title).setHeader("Title").setFlexGrow(1);
        grid.addColumn(VideoPerformance::algorithm).setHeader("Algorithm").setAutoWidth(true);
        grid.addColumn(p -> Formats.dateTime(p.uploadedAt()))
                .setHeader("Uploaded")
                .setAutoWidth(true);
        grid.addColumn(p -> Formats.number(p.views()))
                .setHeader("Views")
                .setAutoWidth(true)
                .setComparator(VideoPerformance::views);
        grid.addColumn(p -> Formats.number(p.likes()))
                .setHeader("Likes")
                .setAutoWidth(true)
                .setComparator(VideoPerformance::likes);
        grid.addColumn(p -> Formats.number(p.comments()))
                .setHeader("Comments")
                .setAutoWidth(true)
                .setComparator(VideoPerformance::comments);
        grid.addColumn(p -> Formats.dateTime(p.measuredAt()))
                .setHeader("Measured")
                .setAutoWidth(true);
        grid.addComponentColumn(AnalyticsView::link).setHeader("Short").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();

        Button refresh = new Button("Fetch latest statistics", event -> fetch());
        add(new H2("Analytics"), totals, refresh, grid);
        show();
    }

    void fetch() {
        try {
            int count = analyticsService.refresh();
            Formats.success("Updated statistics for " + count + " videos");
        } catch (YouTubeException e) {
            Formats.error(e.getMessage());
        }
        show();
    }

    private void show() {
        List<VideoPerformance> performance = analyticsService.performance();
        AnalyticsTotals sum = AnalyticsTotals.of(performance);
        totals.setText(sum.videos() + " videos · " + Formats.number(sum.views()) + " views · "
                + Formats.number(sum.likes()) + " likes · " + Formats.number(sum.comments()) + " comments");
        grid.setItems(performance);
    }

    private static Component link(VideoPerformance performance) {
        Anchor anchor = new Anchor(performance.url(), "Open");
        anchor.setTarget("_blank");
        return anchor;
    }

    Span totals() {
        return totals;
    }

    Grid<VideoPerformance> grid() {
        return grid;
    }
}
