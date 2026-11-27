package io.github.pgatzka.shorty.youtube.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import io.github.pgatzka.shorty.youtube.domain.UploadRequest;
import io.github.pgatzka.shorty.youtube.domain.UploadResult;
import io.github.pgatzka.shorty.youtube.domain.VideoStatistics;
import io.github.pgatzka.shorty.youtube.domain.YouTubeException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YouTubeServiceTest {

    private final YouTubeAuthService authService = mock(YouTubeAuthService.class);
    private FakeGoogle google;
    private YouTubeService service;

    @TempDir
    Path dir;

    @BeforeEach
    void setUp() {
        google = new FakeGoogle();
        Credential credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                .setTransport(google)
                .setJsonFactory(GsonFactory.getDefaultInstance())
                .setTokenServerEncodedUrl(YouTubeAuthService.TOKEN_URL)
                .setClientAuthentication(new ClientParametersAuthentication("id", "secret"))
                .build()
                .setRefreshToken("rt");
        when(authService.credential()).thenReturn(credential);
        service = new YouTubeService(
                authService, YouTubeAuthServiceTest.PROPERTIES, google, GsonFactory.getDefaultInstance());
    }

    private void tokenOk() {
        google.on("POST", "oauth2.googleapis.com/token", 200, "{\"access_token\":\"at\",\"expires_in\":3600}");
    }

    private Path video() throws Exception {
        return Files.write(dir.resolve("v.mp4"), new byte[] {1, 2, 3, 4, 5});
    }

    @Test
    void uploadsResumably() throws Exception {
        tokenOk();
        google.on(
                "POST",
                "uploadType=resumable",
                () -> new MockLowLevelHttpResponse()
                        .setStatusCode(200)
                        .addHeader("Location", "https://upload.test/session"));
        google.on("PUT", "upload.test/session", 200, "{\"id\":\"vid123\"}");

        UploadResult result = service.upload(
                new UploadRequest(video(), "Bubble Sort #shorts", "desc", List.of("sorting"), "unlisted"));

        assertThat(result.videoId()).isEqualTo("vid123");
        assertThat(result.url()).isEqualTo("https://youtube.com/shorts/vid123");
        FakeGoogle.Call init = google.calls().stream()
                .filter(call -> call.url().contains("uploadType=resumable"))
                .findFirst()
                .orElseThrow();
        assertThat(init.url()).contains("part=snippet").contains("part=status");
        assertThat(init.body())
                .contains("\"title\":\"Bubble Sort #shorts\"")
                .contains("\"privacyStatus\":\"unlisted\"")
                .contains("\"selfDeclaredMadeForKids\":false")
                .contains("\"categoryId\":\"28\"");
    }

    @Test
    void revokedTokenMarksConnectionAndFails() throws Exception {
        google.on(
                "POST",
                "oauth2.googleapis.com/token",
                400,
                "{\"error\":\"invalid_grant\",\"error_description\":\"x\"}");
        UploadRequest request = new UploadRequest(video(), "t", null, null, null);

        assertThatThrownBy(() -> service.upload(request))
                .isInstanceOf(YouTubeException.class)
                .hasMessageContaining("revoked");
        verify(authService).markRevoked("YouTube access was revoked or expired. Connect YouTube again.");
    }

    @Test
    void otherTokenErrorsAreNotRevocations() throws Exception {
        google.on("POST", "oauth2.googleapis.com/token", 401, "{\"error\":\"invalid_client\"}");
        UploadRequest request = new UploadRequest(video(), "t", null, null, null);

        assertThatThrownBy(() -> service.upload(request)).hasMessageContaining("invalid_client");
        verify(authService, never()).markRevoked(anyString());
    }

    @Test
    void apiErrorsCarryGooglesMessage() throws Exception {
        tokenOk();
        google.on("POST", "uploadType=resumable", 403, """
                {"error":{"code":403,"message":"quotaExceeded","errors":[{"reason":"quotaExceeded"}]}}""");
        UploadRequest request = new UploadRequest(video(), "t", null, null, null);

        assertThatThrownBy(() -> service.upload(request)).hasMessage("Upload failed: quotaExceeded");
    }

    @Test
    void missingFileIsReported() {
        tokenOk();
        UploadRequest request = new UploadRequest(dir.resolve("missing.mp4"), "t", null, null, null);
        assertThatThrownBy(() -> service.upload(request)).hasMessageStartingWith("Upload failed");
    }

    @Test
    void missingVideoIdIsReported() throws Exception {
        tokenOk();
        google.on(
                "POST",
                "uploadType=resumable",
                () -> new MockLowLevelHttpResponse().setStatusCode(200).addHeader("Location", "https://upload.test/s"));
        google.on("PUT", "upload.test/s", 200, "{}");
        UploadRequest request = new UploadRequest(video(), "t", null, null, null);

        assertThatThrownBy(() -> service.upload(request)).hasMessageContaining("did not return a video id");
    }

    @Test
    void readsStatisticsInBatches() {
        tokenOk();
        google.on("GET", "videos", 200, """
                {"items":[
                  {"id":"a","statistics":{"viewCount":"10","likeCount":"2","commentCount":"1"}},
                  {"id":"b","statistics":{}},
                  {"id":"c"}
                ]}""");
        List<String> ids = IntStream.range(0, 51).mapToObj(i -> "id" + i).toList();

        List<VideoStatistics> statistics = service.statistics(ids);

        assertThat(statistics)
                .contains(new VideoStatistics("a", 10, 2, 1), new VideoStatistics("b", 0, 0, 0))
                .hasSize(4);
        assertThat(google.calls().stream().filter(call -> call.method().equals("GET")))
                .hasSize(2);
    }

    @Test
    void emptyStatisticsResponse() {
        tokenOk();
        google.on("GET", "videos", 200, "{}");
        assertThat(service.statistics(List.of("x"))).isEmpty();
        assertThat(service.statistics(List.of())).isEmpty();
    }

    @Test
    void statisticsErrorsAreTranslated() {
        tokenOk();
        google.on("GET", "videos", 500, "oops");
        assertThatThrownBy(() -> service.statistics(List.of("x"))).hasMessageStartingWith("Reading statistics failed");
    }

    @Test
    void uploadRequestDefaults() {
        UploadRequest request = new UploadRequest(Path.of("a.mp4"), "t", null, null, null);
        assertThat(request.description()).isEmpty();
        assertThat(request.tags()).isEmpty();
        assertThat(request.privacyStatus()).isEqualTo("public");
    }
}
