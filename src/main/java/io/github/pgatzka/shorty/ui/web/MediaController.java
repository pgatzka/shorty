package io.github.pgatzka.shorty.ui.web;

import io.github.pgatzka.shorty.render.service.VideoRenderer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Serves rendered MP4 files to the preview player (with HTTP range support for seeking). */
@RestController
class MediaController {

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9._-]+\\.mp4");
    private static final MediaType MP4 = MediaType.parseMediaType("video/mp4");

    private final VideoRenderer renderer;

    MediaController(VideoRenderer renderer) {
        this.renderer = renderer;
    }

    @GetMapping("/media/{fileName:.+}")
    ResponseEntity<Resource> media(@PathVariable("fileName") String fileName) {
        if (!SAFE_NAME.matcher(fileName).matches()) {
            return ResponseEntity.notFound().build();
        }
        Path directory = renderer.outputDirectory();
        Path file = directory.resolve(fileName).normalize();
        if (!file.startsWith(directory) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MP4).body(new FileSystemResource(file));
    }
}
