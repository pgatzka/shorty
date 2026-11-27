package io.github.pgatzka.shorty.youtube.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.pgatzka.shorty.youtube.domain.YouTubeException;
import io.github.pgatzka.shorty.youtube.service.YouTubeAuthService;
import org.junit.jupiter.api.Test;

class OAuthCallbackControllerTest {

    private final YouTubeAuthService authService = mock(YouTubeAuthService.class);
    private final OAuthCallbackController controller = new OAuthCallbackController(authService);

    @Test
    void successRedirectsToPostView() {
        assertThat(controller.callback("code", "state", null)).isEqualTo("redirect:/post?connected=1");
        verify(authService).completeAuthorization("code", "state");
    }

    @Test
    void googleErrorIsShown() {
        assertThat(controller.callback(null, null, "access_denied"))
                .isEqualTo("redirect:/post?oauthError=Google+reported%3A+access_denied");
        verifyNoInteractions(authService);
    }

    @Test
    void exchangeErrorIsShown() {
        doThrow(new YouTubeException("stale")).when(authService).completeAuthorization("c", "s");
        assertThat(controller.callback("c", "s", null)).isEqualTo("redirect:/post?oauthError=stale");
    }
}
