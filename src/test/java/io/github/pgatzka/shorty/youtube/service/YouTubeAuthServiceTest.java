package io.github.pgatzka.shorty.youtube.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.json.gson.GsonFactory;
import io.github.pgatzka.shorty.youtube.domain.ConnectionStatus;
import io.github.pgatzka.shorty.youtube.domain.YouTubeCredential;
import io.github.pgatzka.shorty.youtube.domain.YouTubeException;
import io.github.pgatzka.shorty.youtube.repository.YouTubeCredentialRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.util.UriComponentsBuilder;

class YouTubeAuthServiceTest {

    static final YouTubeProperties PROPERTIES = new YouTubeProperties(
            "client-id", "client-secret", "http://localhost:8080/oauth2/callback", "28", "shorty");
    static final Instant NOW = Instant.parse("2026-11-27T12:00:00Z");

    private final TokenCipher cipher = new TokenCipher(TokenCipherTest.KEY);
    private final YouTubeCredentialRepository repository = mock(YouTubeCredentialRepository.class);
    private FakeGoogle google;
    private YouTubeAuthService service;

    @BeforeEach
    void setUp() {
        google = new FakeGoogle();
        service = service(PROPERTIES, cipher);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private YouTubeAuthService service(YouTubeProperties properties, TokenCipher tokenCipher) {
        return new YouTubeAuthService(
                properties,
                tokenCipher,
                repository,
                google,
                GsonFactory.getDefaultInstance(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void authorizationUrlAsksForOfflineAccess() {
        URI url = URI.create(service.authorizationUrl());
        var params = UriComponentsBuilder.fromUri(url).build().getQueryParams();

        assertThat(url.toString()).startsWith(YouTubeAuthService.AUTH_URL);
        assertThat(params.getFirst("client_id")).isEqualTo("client-id");
        assertThat(params.getFirst("access_type")).isEqualTo("offline");
        assertThat(params.getFirst("prompt")).isEqualTo("consent");
        assertThat(params.getFirst("response_type")).isEqualTo("code");
        assertThat(params.getFirst("state")).hasSize(32);
        assertThat(params.getFirst("scope")).contains("youtube.upload");
    }

    @Test
    void completesFromPastedRedirectUrlAndStoresEncryptedToken() {
        google.on("POST", "oauth2.googleapis.com/token", 200, """
                {"access_token":"at","refresh_token":"rt-123","expires_in":3600,"token_type":"Bearer"}""");
        when(repository.findById(1)).thenReturn(Optional.empty());
        String state = stateOf(service.authorizationUrl());

        service.completeAuthorization(
                "http://localhost:8080/oauth2/callback?state=" + state + "&code=4%2F0Abc&scope=x");

        ArgumentCaptor<YouTubeCredential> saved = ArgumentCaptor.forClass(YouTubeCredential.class);
        verify(repository).save(saved.capture());
        assertThat(cipher.decrypt(saved.getValue().getEncryptedRefreshToken())).isEqualTo("rt-123");
        assertThat(saved.getValue().getConnectedAt()).isEqualTo(NOW);
        assertThat(google.calls().getFirst().body()).contains("code=4%2F0Abc").contains("client_secret=client-secret");
    }

    @Test
    void acceptsBareCodeAndQueryString() {
        google.on("POST", "token", 200, """
                {"access_token":"at","refresh_token":"rt","expires_in":3600}""");
        YouTubeCredential existing = new YouTubeCredential(cipher.encrypt("old"), Instant.EPOCH);
        existing.markRevoked("revoked");
        when(repository.findById(1)).thenReturn(Optional.of(existing));

        service.completeAuthorization("  bare-code ");
        assertThat(existing.isRevoked()).isFalse();
        assertThat(existing.getLastError()).isNull();
        assertThat(cipher.decrypt(existing.getEncryptedRefreshToken())).isEqualTo("rt");

        service.completeAuthorization("code=abc");
        assertThat(google.calls()).hasSize(2);
    }

    @Test
    void rejectsBadInput() {
        assertThatThrownBy(() -> service.completeAuthorization(" ")).isInstanceOf(YouTubeException.class);
        assertThatThrownBy(() -> service.completeAuthorization((String) null)).isInstanceOf(YouTubeException.class);
        assertThatThrownBy(() -> service.completeAuthorization("?error=access_denied&code="))
                .hasMessageContaining("access_denied");
        assertThatThrownBy(() -> service.completeAuthorization("", null)).hasMessageContaining("No authorization code");
        service.authorizationUrl();
        assertThatThrownBy(() -> service.completeAuthorization("code=x&state=forged"))
                .hasMessageContaining("stale");
    }

    @Test
    void failedExchangeIsReported() {
        google.on("POST", "token", 400, "{\"error\":\"invalid_grant\"}");
        assertThatThrownBy(() -> service.completeAuthorization("code"))
                .isInstanceOf(YouTubeException.class)
                .hasMessageContaining("rejected");
        verify(repository, never()).save(any());
    }

    @Test
    void missingRefreshTokenIsReported() {
        google.on("POST", "token", 200, "{\"access_token\":\"at\",\"expires_in\":3600}");
        assertThatThrownBy(() -> service.completeAuthorization("code")).hasMessageContaining("no refresh token");
    }

    @Test
    void credentialUsesStoredToken() {
        when(repository.findById(1)).thenReturn(Optional.of(new YouTubeCredential(cipher.encrypt("rt-9"), NOW)));
        Credential credential = service.credential();
        assertThat(credential.getRefreshToken()).isEqualTo("rt-9");
        assertThat(credential.getTokenServerEncodedUrl()).isEqualTo(YouTubeAuthService.TOKEN_URL);
    }

    @Test
    void credentialRequiresConnection() {
        when(repository.findById(1)).thenReturn(Optional.empty());
        assertThatThrownBy(service::credential).hasMessageContaining("not connected");

        YouTubeCredential revoked = new YouTubeCredential(cipher.encrypt("rt"), NOW);
        revoked.markRevoked("gone");
        when(repository.findById(1)).thenReturn(Optional.of(revoked));
        assertThatThrownBy(service::credential).hasMessageContaining("revoked");
    }

    @Test
    void statusReflectsStoredCredential() {
        when(repository.findById(1)).thenReturn(Optional.empty());
        assertThat(service.status()).isEqualTo(new ConnectionStatus(true, false, null, null));

        YouTubeCredential credential = new YouTubeCredential(cipher.encrypt("rt"), NOW);
        when(repository.findById(1)).thenReturn(Optional.of(credential));
        assertThat(service.status()).isEqualTo(new ConnectionStatus(true, true, NOW, null));

        service.markRevoked("Token revoked");
        assertThat(service.status()).isEqualTo(new ConnectionStatus(true, false, NOW, "Token revoked"));
        verify(repository).save(credential);
    }

    @Test
    void markRevokedWithoutCredentialDoesNothing() {
        when(repository.findById(1)).thenReturn(Optional.empty());
        service.markRevoked("x");
        verify(repository, never()).save(any());
    }

    @Test
    void disconnectDeletesCredential() {
        service.disconnect();
        verify(repository).deleteById(1);
    }

    @Test
    void unconfiguredServiceExplainsWhatIsMissing() {
        YouTubeAuthService noClient = service(new YouTubeProperties("", "", "r", "28", "s"), cipher);
        assertThat(noClient.isConfigured()).isFalse();
        assertThatThrownBy(noClient::authorizationUrl).hasMessageContaining("SHORTY_GOOGLE_CLIENT_ID");

        YouTubeAuthService noKey = service(PROPERTIES, new TokenCipher(""));
        assertThat(noKey.isConfigured()).isFalse();
        assertThatThrownBy(noKey::authorizationUrl).hasMessageContaining("SHORTY_ENCRYPTION_KEY");
    }

    private static String stateOf(String url) {
        return UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
    }
}
