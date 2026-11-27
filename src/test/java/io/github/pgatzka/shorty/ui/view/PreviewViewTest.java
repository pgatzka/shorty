package io.github.pgatzka.shorty.ui.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaadin.flow.component.button.Button;
import io.github.pgatzka.shorty.publishing.service.PublishingService;
import io.github.pgatzka.shorty.render.domain.RenderedVideo;
import io.github.pgatzka.shorty.render.domain.VisualStyle;
import io.github.pgatzka.shorty.render.service.RenderException;
import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class PreviewViewTest extends ViewTestBase {

    @MockitoBean
    PublishingService publishingService;

    @Test
    void rendersAndShowsThePlayer() {
        RenderedVideo video = new RenderedVideo(
                Path.of("videos/shorty-1.mp4"),
                SortAlgorithm.MERGE,
                VisualStyle.SCATTER_DOTS,
                150,
                58.2,
                1000,
                0,
                2000);
        when(publishingService.renderPreview(SortAlgorithm.MERGE, VisualStyle.CLASSIC_BARS))
                .thenReturn(CompletableFuture.completedFuture(video));
        PreviewView view = navigate(PreviewView.class);

        test(view.algorithmField()).selectItem("Merge Sort");
        test($(Button.class).withText("Render preview").single()).click();

        assertThat(view.status().getText()).isEqualTo("Done: shorty-1.mp4");
        assertThat(view.player().isVisible()).isTrue();
        assertThat(view.player().getSource()).isEqualTo("/media/shorty-1.mp4");
    }

    @Test
    void showsRenderErrors() {
        when(publishingService.isBusy()).thenReturn(true);
        when(publishingService.renderPreview(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RenderException("ffmpeg missing")));
        PreviewView view = navigate(PreviewView.class);

        test($(Button.class).withText("Render preview").single()).click();

        assertThat(view.status().getText()).isEqualTo("Render failed: ffmpeg missing");
        assertThat(view.player().isVisible()).isFalse();
    }

    @Test
    void pendingRenderShowsProgress() {
        when(publishingService.renderPreview(any(), any())).thenReturn(new CompletableFuture<>());
        PreviewView view = navigate(PreviewView.class);

        test($(Button.class).withText("Render preview").single()).click();

        assertThat(view.status().getText()).isEqualTo("Rendering…");
    }

    @Test
    void requiresAlgorithmAndStyle() {
        PreviewView view = navigate(PreviewView.class);
        view.algorithmField().clear();

        view.startRender();

        verify(publishingService, never()).renderPreview(any(), any());
        assertThat(notifications()).contains("Pick an algorithm and a style");
    }
}
