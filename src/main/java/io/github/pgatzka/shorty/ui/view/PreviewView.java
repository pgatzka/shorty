package io.github.pgatzka.shorty.ui.view;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import io.github.pgatzka.shorty.publishing.service.PublishingService;
import io.github.pgatzka.shorty.render.domain.RenderedVideo;
import io.github.pgatzka.shorty.render.domain.VisualStyle;
import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import java.util.Locale;

/** Renders a test video and plays it in the browser, without uploading. */
@Route(value = "", layout = MainLayout.class)
@RouteAlias(value = "preview", layout = MainLayout.class)
@PageTitle("Preview | Shorty")
public class PreviewView extends VerticalLayout {

    private static final long serialVersionUID = 1L;
    private static final int POLL_MILLIS = 2000;

    private final transient PublishingService publishingService;
    private final ComboBox<SortAlgorithm> algorithm = new ComboBox<>("Algorithm");
    private final ComboBox<VisualStyle> style = new ComboBox<>("Style");
    private final Button render = new Button("Render preview");
    private final Span status = new Span();
    private final Paragraph details = new Paragraph();
    private final VideoPlayer player = new VideoPlayer();

    public PreviewView(PublishingService publishingService) {
        this.publishingService = publishingService;

        algorithm.setItems(SortAlgorithm.values());
        algorithm.setItemLabelGenerator(SortAlgorithm::displayName);
        algorithm.setValue(SortAlgorithm.BUBBLE);
        algorithm.setRequired(true);
        style.setItems(VisualStyle.values());
        style.setItemLabelGenerator(VisualStyle::displayName);
        style.setValue(VisualStyle.CLASSIC_BARS);
        style.setRequired(true);

        render.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        render.addClickListener(event -> startRender());

        HorizontalLayout controls = new HorizontalLayout(algorithm, style, render);
        controls.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        player.setVisible(false);
        add(
                new H2("Preview"),
                new Paragraph("Render a test video at full quality. Nothing is uploaded. A render takes a minute "
                        + "or two."),
                controls,
                status,
                details,
                player);
    }

    void startRender() {
        if (algorithm.isEmpty() || style.isEmpty()) {
            Formats.error("Pick an algorithm and a style");
            return;
        }
        UI ui = UI.getCurrent();
        render.setEnabled(false);
        status.setText(publishingService.isBusy() ? "Queued behind another render…" : "Rendering…");
        ui.setPollInterval(POLL_MILLIS);
        publishingService
                .renderPreview(algorithm.getValue(), style.getValue())
                .whenComplete((video, error) -> Formats.access(ui, () -> {
                    ui.setPollInterval(-1);
                    render.setEnabled(true);
                    if (error == null) {
                        show(video);
                    } else {
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        status.setText("Render failed: " + cause.getMessage());
                        Formats.error("Render failed: " + cause.getMessage());
                    }
                }));
    }

    private void show(RenderedVideo video) {
        status.setText("Done: " + video.fileName());
        details.setText(String.format(
                Locale.ROOT,
                "%s, %s, %d bars, %.1f s, %s comparisons, %s swaps, %s array accesses",
                video.algorithm().displayName(),
                video.style().displayName(),
                video.barCount(),
                video.durationSeconds(),
                Formats.number(video.comparisons()),
                Formats.number(video.swaps()),
                Formats.number(video.accesses())));
        player.setSource("/media/" + video.fileName());
        player.setVisible(true);
    }

    ComboBox<SortAlgorithm> algorithmField() {
        return algorithm;
    }

    Span status() {
        return status;
    }

    VideoPlayer player() {
        return player;
    }
}
