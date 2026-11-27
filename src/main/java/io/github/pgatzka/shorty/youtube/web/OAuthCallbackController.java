package io.github.pgatzka.shorty.youtube.web;

import io.github.pgatzka.shorty.youtube.domain.YouTubeException;
import io.github.pgatzka.shorty.youtube.service.YouTubeAuthService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Receives Google's OAuth redirect when the UI is opened via the registered redirect host. */
@Controller
class OAuthCallbackController {

    static final String RESULT_VIEW = "/post";

    private final YouTubeAuthService authService;

    OAuthCallbackController(YouTubeAuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/oauth2/callback")
    String callback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error) {
        if (error != null) {
            return failure("Google reported: " + error);
        }
        try {
            authService.completeAuthorization(code, state);
            return "redirect:" + RESULT_VIEW + "?connected=1";
        } catch (YouTubeException e) {
            return failure(e.getMessage());
        }
    }

    private static String failure(String message) {
        return "redirect:" + RESULT_VIEW + "?oauthError=" + URLEncoder.encode(message, StandardCharsets.UTF_8);
    }
}
