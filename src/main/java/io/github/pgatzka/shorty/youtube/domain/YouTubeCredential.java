package io.github.pgatzka.shorty.youtube.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** The single stored YouTube connection. The refresh token is stored encrypted. */
@Entity
@Table(name = "youtube_credential")
public class YouTubeCredential {

    /** There is only ever one connection. */
    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    @Column(name = "encrypted_refresh_token", nullable = false)
    private String encryptedRefreshToken;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "last_error")
    private String lastError;

    protected YouTubeCredential() {
        // for JPA
    }

    public YouTubeCredential(String encryptedRefreshToken, Instant connectedAt) {
        this.id = SINGLETON_ID;
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.connectedAt = connectedAt;
    }

    public Integer getId() {
        return id;
    }

    public String getEncryptedRefreshToken() {
        return encryptedRefreshToken;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public String getLastError() {
        return lastError;
    }

    /** Replaces the token after a fresh authorization and clears any revocation. */
    public void reconnect(String encryptedRefreshToken, Instant connectedAt) {
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.connectedAt = connectedAt;
        this.revoked = false;
        this.lastError = null;
    }

    public void markRevoked(String error) {
        this.revoked = true;
        this.lastError = error;
    }
}
