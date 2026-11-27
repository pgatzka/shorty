package io.github.pgatzka.shorty.ui.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.pgatzka.shorty.publishing.domain.PublishTrigger;
import io.github.pgatzka.shorty.publishing.domain.UploadRecord;
import io.github.pgatzka.shorty.publishing.service.PublishingService;
import io.github.pgatzka.shorty.render.domain.VisualStyle;
import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import io.github.pgatzka.shorty.youtube.domain.ConnectionStatus;
import io.github.pgatzka.shorty.youtube.domain.YouTubeException;
import io.github.pgatzka.shorty.youtube.service.YouTubeAuthService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

class PostNowViewTest extends ViewTestBase {

    private static final ConnectionStatus CONNECTED = new ConnectionStatus(true, true, Instant.EPOCH, null);

    @MockitoBean
    PublishingService publishingService;

    @MockitoBean
    YouTubeAuthService authService;

    @Test
    void unconfiguredDisablesEverything() {
        when(authService.status()).thenReturn(new ConnectionStatus(false, false, null, null));
        PostNowView view = navigate(PostNowView.class);

        assertThat(view.connection().getText()).startsWith("Not configured");
        assertThat(view.postButton().isEnabled()).isFalse();
        assertThat(view.connectButton().isEnabled()).isFalse();
        assertThat(view.disconnectButton().isEnabled()).isFalse();
    }

    @Test
    void postNowQueuesAPublish() {
        when(authService.status()).thenReturn(CONNECTED);
        UploadRecord upload =
                new UploadRecord(PublishTrigger.MANUAL, SortAlgorithm.QUICK, VisualStyle.RAINBOW_BARS, Instant.EPOCH);
        ReflectionTestUtils.setField(upload, "id", 7L);
        when(publishingService.startPublish(PublishTrigger.MANUAL)).thenReturn(upload);
        PostNowView view = navigate(PostNowView.class);

        test(view.postButton()).click();

        verify(publishingService).startPublish(PublishTrigger.MANUAL);
        assertThat(notifications()).anyMatch(text -> text.startsWith("Queued #7: Quick Sort, Rainbow bars"));
    }

    @Test
    void connectOpensConsentScreen() {
        when(authService.status()).thenReturn(new ConnectionStatus(true, false, null, null));
        when(authService.authorizationUrl()).thenReturn("https://accounts.google.com/o/oauth2/v2/auth?x");
        PostNowView view = navigate(PostNowView.class);

        test(view.connectButton()).click();

        verify(authService).authorizationUrl();
    }

    @Test
    void connectErrorsAreShown() {
        when(authService.status()).thenReturn(new ConnectionStatus(true, false, null, null));
        when(authService.authorizationUrl()).thenThrow(new YouTubeException("no client"));
        PostNowView view = navigate(PostNowView.class);

        test(view.connectButton()).click();

        assertThat(notifications()).contains("no client");
    }

    @Test
    void finishConnectingWithPastedUrl() {
        when(authService.status()).thenReturn(new ConnectionStatus(true, false, null, null));
        PostNowView view = navigate(PostNowView.class);

        test(view.pastedField()).setValue("http://localhost:8080/oauth2/callback?code=abc");
        test(view.finishButton()).click();

        verify(authService).completeAuthorization("http://localhost:8080/oauth2/callback?code=abc");
        assertThat(view.pastedField().getValue()).isEmpty();
        assertThat(notifications()).contains("YouTube connected");
    }

    @Test
    void finishConnectingErrorsAreShown() {
        when(authService.status()).thenReturn(new ConnectionStatus(true, false, null, null));
        doThrow(new YouTubeException("bad code")).when(authService).completeAuthorization("x");
        PostNowView view = navigate(PostNowView.class);

        test(view.pastedField()).setValue("x");
        test(view.finishButton()).click();

        assertThat(notifications()).contains("bad code");
    }

    @Test
    void disconnect() {
        when(authService.status()).thenReturn(CONNECTED);
        PostNowView view = navigate(PostNowView.class);

        test(view.disconnectButton()).click();

        verify(authService).disconnect();
    }

    @Test
    void showsOAuthResultFromRedirect() {
        when(authService.status()).thenReturn(CONNECTED);
        navigate("post?connected=1", PostNowView.class);
        assertThat(notifications()).contains("YouTube connected");

        navigate("post?oauthError=denied", PostNowView.class);
        assertThat(notifications()).contains("Connecting failed: denied");
    }

    @Test
    void describesEveryState() {
        assertThat(PostNowView.describe(new ConnectionStatus(true, true, Instant.EPOCH, null)))
                .startsWith("Connected since");
        assertThat(PostNowView.describe(new ConnectionStatus(true, false, Instant.EPOCH, "revoked")))
                .isEqualTo("Not connected: revoked");
        assertThat(PostNowView.describe(new ConnectionStatus(true, false, null, null)))
                .isEqualTo("Not connected yet.");
    }
}
