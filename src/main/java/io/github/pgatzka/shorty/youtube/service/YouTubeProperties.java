package io.github.pgatzka.shorty.youtube.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * YouTube settings ({@code shorty.youtube.*}). Secrets come from the environment, never the repo.
 *
 * @param clientId Google OAuth client id
 * @param clientSecret Google OAuth client secret
 * @param redirectUri OAuth redirect URI registered for the client
 * @param categoryId YouTube category of uploads (28 = Science &amp; Technology)
 * @param applicationName application name sent to the API
 */
@ConfigurationProperties("shorty.youtube")
public record YouTubeProperties(
        @DefaultValue("") String clientId,
        @DefaultValue("") String clientSecret,

        @DefaultValue("http://localhost:8080/oauth2/callback")
        String redirectUri,

        @DefaultValue("28") String categoryId,
        @DefaultValue("shorty") String applicationName) {

    public boolean hasClient() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }
}
