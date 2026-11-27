package io.github.pgatzka.shorty.ui.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.pgatzka.shorty.render.service.VideoRenderer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

class MediaControllerTest {

    @TempDir
    Path dir;

    private MediaController controller;

    @BeforeEach
    void setUp() throws Exception {
        Path videos = Files.createDirectories(dir.resolve("videos"));
        Files.writeString(videos.resolve("a.mp4"), "data");
        Files.writeString(dir.resolve("secret.mp4"), "outside");
        VideoRenderer renderer = mock(VideoRenderer.class);
        when(renderer.outputDirectory()).thenReturn(videos);
        controller = new MediaController(renderer);
    }

    @Test
    void servesVideos() {
        var response = controller.media("a.mp4");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).hasToString("video/mp4");
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void rejectsUnknownAndUnsafeNames() {
        assertThat(controller.media("missing.mp4").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(controller.media("../secret.mp4").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(controller.media("..").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(controller.media("a.txt").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
