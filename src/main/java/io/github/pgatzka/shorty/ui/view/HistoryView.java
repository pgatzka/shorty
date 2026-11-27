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
import io.github.pgatzka.shorty.publishing.domain.UploadRecord;
import io.github.pgatzka.shorty.publishing.domain.UploadStatus;
import io.github.pgatzka.shorty.publishing.service.PublishingService;

/** Upload history, newest first. */
@Route(value = "history", layout = MainLayout.class)
@PageTitle("History | Shorty")
public class HistoryView extends VerticalLayout {

    private static final long serialVersionUID = 1L;
    static final int LIMIT = 200;

    private final transient PublishingService publishingService;
    private final Grid<UploadRecord> grid = new Grid<>(UploadRecord.class, false);

    public HistoryView(PublishingService publishingService) {
        this.publishingService = publishingService;
        setSizeFull();

        grid.addColumn(UploadRecord::getId).setHeader("#").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(upload -> Formats.dateTime(upload.getCreatedAt()))
                .setHeader("Created")
                .setAutoWidth(true);
        grid.addColumn(UploadRecord::getTrigger).setHeader("Trigger").setAutoWidth(true);
        grid.addComponentColumn(HistoryView::statusBadge).setHeader("Status").setAutoWidth(true);
        grid.addColumn(upload -> upload.getAlgorithm().displayName())
                .setHeader("Algorithm")
                .setAutoWidth(true);
        grid.addColumn(upload -> upload.getStyle().displayName())
                .setHeader("Style")
                .setAutoWidth(true);
        grid.addColumn(UploadRecord::getBarCount).setHeader("Bars").setAutoWidth(true);
        grid.addColumn(upload -> Formats.seconds(upload.getDurationSeconds()))
                .setHeader("Length")
                .setAutoWidth(true);
        grid.addColumn(UploadRecord::getTitle).setHeader("Title").setFlexGrow(1);
        grid.addComponentColumn(HistoryView::link).setHeader("Short").setAutoWidth(true);
        grid.addColumn(UploadRecord::getErrorMessage).setHeader("Error").setFlexGrow(1);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setSizeFull();

        Button refresh = new Button("Refresh", event -> refresh());
        add(new H2("Upload history"), refresh, grid);
        refresh();
    }

    void refresh() {
        grid.setItems(publishingService.recentHistory(LIMIT));
    }

    static Component statusBadge(UploadRecord upload) {
        Span badge = new Span(upload.getStatus().name());
        String variant = switch (upload.getStatus()) {
            case UPLOADED -> "success";
            case FAILED -> "error";
            case RENDERING, UPLOADING -> "contrast";
        };
        badge.getElement().getThemeList().add("badge " + variant);
        return badge;
    }

    static Component link(UploadRecord upload) {
        if (upload.getStatus() != UploadStatus.UPLOADED || upload.url() == null) {
            return new Span();
        }
        Anchor anchor = new Anchor(upload.url(), "Open");
        anchor.setTarget("_blank");
        return anchor;
    }

    Grid<UploadRecord> grid() {
        return grid;
    }
}
