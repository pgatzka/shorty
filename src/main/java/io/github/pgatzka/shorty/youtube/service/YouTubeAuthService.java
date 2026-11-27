package io.github.pgatzka.shorty.youtube.service;

import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.AuthorizationCodeTokenRequest;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import io.github.pgatzka.shorty.youtube.domain.ConnectionStatus;
import io.github.pgatzka.shorty.youtube.domain.YouTubeCredential;
import io.github.pgatzka.shorty.youtube.domain.YouTubeException;
import io.github.pgatzka.shorty.youtube.repository.YouTubeCredentialRepository;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

/** Connects the app to a YouTube channel via OAuth and hands out credentials for API calls. */
@Service
public class YouTubeAuthService {

    static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    static final List<String> SCOPES = List.of(
            "https://www.googleapis.com/auth/youtube.upload", "https://www.googleapis.com/auth/youtube.readonly");

    private final YouTubeProperties properties;
    private final TokenCipher cipher;
    private final YouTubeCredentialRepository repository;
    private final HttpTransport transport;
    private final JsonFactory jsonFactory;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final AtomicReference<String> pendingState = new AtomicReference<>();

    @Autowired
    public YouTubeAuthService(
            YouTubeProperties properties,
            TokenCipher cipher,
            YouTubeCredentialRepository repository,
            HttpTransport transport,
            JsonFactory jsonFactory) {
        this(properties, cipher, repository, transport, jsonFactory, Clock.systemUTC());
    }

    YouTubeAuthService(
            YouTubeProperties properties,
            TokenCipher cipher,
            YouTubeCredentialRepository repository,
            HttpTransport transport,
            JsonFactory jsonFactory,
            Clock clock) {
        this.properties = properties;
        this.cipher = cipher;
        this.repository = repository;
        this.transport = transport;
        this.jsonFactory = jsonFactory;
        this.clock = clock;
    }

    /** Client id, secret and encryption key are all present. */
    public boolean isConfigured() {
        return properties.hasClient() && cipher.isConfigured();
    }

    @Transactional(readOnly = true)
    public ConnectionStatus status() {
        Optional<YouTubeCredential> credential = repository.findById(YouTubeCredential.SINGLETON_ID);
        return new ConnectionStatus(
                isConfigured(),
                credential.map(c -> !c.isRevoked()).orElse(false),
                credential.map(YouTubeCredential::getConnectedAt).orElse(null),
                credential.map(YouTubeCredential::getLastError).orElse(null));
    }

    /** URL of Google's consent screen. Starts a new authorization attempt. */
    public String authorizationUrl() {
        requireConfigured();
        String state = HexFormat.of().formatHex(randomBytes());
        pendingState.set(state);
        return new AuthorizationCodeRequestUrl(AUTH_URL, properties.clientId())
                .setRedirectUri(properties.redirectUri())
                .setScopes(SCOPES)
                .setState(state)
                .set("access_type", "offline")
                .set("prompt", "consent")
                .build();
    }

    /**
     * Completes authorization from what the user pasted: the full redirect URL, its query string,
     * or just the code.
     */
    public void completeAuthorization(String pasted) {
        if (pasted == null || pasted.isBlank()) {
            throw new YouTubeException("Paste the URL Google redirected you to, or the code from it");
        }
        String input = pasted.strip();
        if (!input.contains("code=")) {
            completeAuthorization(input, null);
            return;
        }
        String query = input.contains("?") ? input : "?" + input;
        MultiValueMap<String, String> params =
                UriComponentsBuilder.fromUriString(query).build().getQueryParams();
        String error = params.getFirst("error");
        if (error != null) {
            throw new YouTubeException("Google reported: " + decode(error));
        }
        completeAuthorization(decode(params.getFirst("code")), decode(params.getFirst("state")));
    }

    /** Exchanges an authorization code for a refresh token and stores it encrypted. */
    @Transactional
    public void completeAuthorization(String code, String state) {
        requireConfigured();
        if (code == null || code.isBlank()) {
            throw new YouTubeException("No authorization code received");
        }
        if (state != null && !state.equals(pendingState.get())) {
            throw new YouTubeException("This authorization is stale. Click Connect YouTube again.");
        }
        TokenResponse response;
        try {
            response = new AuthorizationCodeTokenRequest(transport, jsonFactory, new GenericUrl(TOKEN_URL), code)
                    .setRedirectUri(properties.redirectUri())
                    .setClientAuthentication(clientAuthentication())
                    .execute();
        } catch (IOException e) {
            throw new YouTubeException("Google rejected the authorization code: " + e.getMessage(), e);
        }
        String refreshToken = response.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new YouTubeException("Google returned no refresh token. Remove Shorty's access in your "
                    + "Google account permissions and connect again.");
        }
        String encrypted = cipher.encrypt(refreshToken);
        YouTubeCredential credential = repository
                .findById(YouTubeCredential.SINGLETON_ID)
                .map(existing -> {
                    existing.reconnect(encrypted, clock.instant());
                    return existing;
                })
                .orElseGet(() -> new YouTubeCredential(encrypted, clock.instant()));
        repository.save(credential);
        pendingState.set(null);
    }

    /** A credential that refreshes its access token on demand. */
    @Transactional(readOnly = true)
    public Credential credential() {
        requireConfigured();
        YouTubeCredential stored = repository
                .findById(YouTubeCredential.SINGLETON_ID)
                .orElseThrow(() -> new YouTubeException("YouTube is not connected"));
        if (stored.isRevoked()) {
            throw new YouTubeException("YouTube access was revoked. Connect YouTube again.");
        }
        return new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                .setTransport(transport)
                .setJsonFactory(jsonFactory)
                .setTokenServerEncodedUrl(TOKEN_URL)
                .setClientAuthentication(clientAuthentication())
                .build()
                .setRefreshToken(cipher.decrypt(stored.getEncryptedRefreshToken()));
    }

    @Transactional
    public void markRevoked(String error) {
        repository.findById(YouTubeCredential.SINGLETON_ID).ifPresent(credential -> {
            credential.markRevoked(error);
            repository.save(credential);
        });
    }

    @Transactional
    public void disconnect() {
        repository.deleteById(YouTubeCredential.SINGLETON_ID);
    }

    private ClientParametersAuthentication clientAuthentication() {
        return new ClientParametersAuthentication(properties.clientId(), properties.clientSecret());
    }

    private void requireConfigured() {
        if (!properties.hasClient()) {
            throw new YouTubeException(
                    "Google OAuth client is not configured (SHORTY_GOOGLE_CLIENT_ID / SHORTY_GOOGLE_CLIENT_SECRET)");
        }
        if (!cipher.isConfigured()) {
            throw new YouTubeException("Encryption key (SHORTY_ENCRYPTION_KEY) is not configured");
        }
    }

    private byte[] randomBytes() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return bytes;
    }

    private static String decode(String value) {
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
