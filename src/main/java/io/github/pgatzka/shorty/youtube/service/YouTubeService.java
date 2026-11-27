package io.github.pgatzka.shorty.youtube.service;

import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import io.github.pgatzka.shorty.youtube.domain.UploadRequest;
import io.github.pgatzka.shorty.youtube.domain.UploadResult;
import io.github.pgatzka.shorty.youtube.domain.VideoStatistics;
import io.github.pgatzka.shorty.youtube.domain.YouTubeException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

/** Uploads videos and reads their statistics. */
@Service
public class YouTubeService {

    static final int MAX_IDS_PER_REQUEST = 50;

    private final YouTubeAuthService authService;
    private final YouTubeProperties properties;
    private final HttpTransport transport;
    private final JsonFactory jsonFactory;

    public YouTubeService(
            YouTubeAuthService authService,
            YouTubeProperties properties,
            HttpTransport transport,
            JsonFactory jsonFactory) {
        this.authService = authService;
        this.properties = properties;
        this.transport = transport;
        this.jsonFactory = jsonFactory;
    }

    public UploadResult upload(UploadRequest request) {
        YouTube youTube = client();
        Video video = new Video()
                .setSnippet(new VideoSnippet()
                        .setTitle(request.title())
                        .setDescription(request.description())
                        .setTags(request.tags())
                        .setCategoryId(properties.categoryId()))
                .setStatus(new VideoStatus()
                        .setPrivacyStatus(request.privacyStatus())
                        .setSelfDeclaredMadeForKids(false));
        try (InputStream input = new BufferedInputStream(Files.newInputStream(request.file()))) {
            InputStreamContent content = new InputStreamContent("video/mp4", input);
            content.setLength(Files.size(request.file()));
            YouTube.Videos.Insert insert = youTube.videos().insert(List.of("snippet", "status"), video, content);
            insert.getMediaHttpUploader().setDirectUploadEnabled(false);
            Video uploaded = insert.execute();
            if (uploaded == null || uploaded.getId() == null) {
                throw new YouTubeException("YouTube did not return a video id");
            }
            return new UploadResult(uploaded.getId());
        } catch (IOException e) {
            throw translate("Upload failed", e);
        }
    }

    /** Statistics for the given video ids; unknown or deleted videos are left out. */
    public List<VideoStatistics> statistics(Collection<String> videoIds) {
        if (videoIds.isEmpty()) {
            return List.of();
        }
        YouTube youTube = client();
        List<String> ids = List.copyOf(videoIds);
        List<VideoStatistics> result = new ArrayList<>(ids.size());
        try {
            for (int from = 0; from < ids.size(); from += MAX_IDS_PER_REQUEST) {
                List<String> batch = ids.subList(from, Math.min(ids.size(), from + MAX_IDS_PER_REQUEST));
                VideoListResponse response = youTube.videos()
                        .list(List.of("statistics"))
                        .setId(batch)
                        .setMaxResults((long) MAX_IDS_PER_REQUEST)
                        .execute();
                if (response.getItems() == null) {
                    continue;
                }
                for (Video video : response.getItems()) {
                    if (video.getStatistics() != null) {
                        result.add(new VideoStatistics(
                                video.getId(),
                                count(video.getStatistics().getViewCount()),
                                count(video.getStatistics().getLikeCount()),
                                count(video.getStatistics().getCommentCount())));
                    }
                }
            }
            return result;
        } catch (IOException e) {
            throw translate("Reading statistics failed", e);
        }
    }

    private YouTube client() {
        return new YouTube.Builder(transport, jsonFactory, authService.credential())
                .setApplicationName(properties.applicationName())
                .build();
    }

    private YouTubeException translate(String action, IOException e) {
        if (e instanceof TokenResponseException tokenError) {
            String code = tokenError.getDetails() == null
                    ? null
                    : tokenError.getDetails().getError();
            if ("invalid_grant".equals(code)) {
                String message = "YouTube access was revoked or expired. Connect YouTube again.";
                authService.markRevoked(message);
                return new YouTubeException(message, e);
            }
            return new YouTubeException(action + ": token refresh failed (" + code + ")", e);
        }
        if (e instanceof GoogleJsonResponseException apiError && apiError.getDetails() != null) {
            return new YouTubeException(action + ": " + apiError.getDetails().getMessage(), e);
        }
        return new YouTubeException(action + ": " + e.getMessage(), e);
    }

    private static long count(BigInteger value) {
        return value == null ? 0 : value.longValue();
    }
}
