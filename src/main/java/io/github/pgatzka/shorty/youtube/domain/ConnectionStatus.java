package io.github.pgatzka.shorty.youtube.domain;

import java.time.Instant;

/**
 * State of the YouTube connection as shown in the UI.
 *
 * @param configured client id, secret and encryption key are present
 * @param connected a refresh token is stored and not known to be revoked
 * @param connectedAt when the token was stored, or {@code null}
 * @param error last error, for example a revoked token, or {@code null}
 */
public record ConnectionStatus(boolean configured, boolean connected, Instant connectedAt, String error) {}
