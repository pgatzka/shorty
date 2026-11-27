package io.github.pgatzka.shorty.youtube.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.shorty.youtube.domain.YouTubeException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenCipherTest {

    static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    static final String OTHER_KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    @Test
    void roundTrips() {
        TokenCipher cipher = new TokenCipher(KEY);
        String encrypted = cipher.encrypt("refresh-token");

        assertThat(cipher.isConfigured()).isTrue();
        assertThat(encrypted).doesNotContain("refresh-token");
        assertThat(cipher.encrypt("refresh-token")).as("random IV").isNotEqualTo(encrypted);
        assertThat(cipher.decrypt(encrypted)).isEqualTo("refresh-token");
    }

    @Test
    void wrongKeyCannotDecrypt() {
        String encrypted = new TokenCipher(KEY).encrypt("secret");
        assertThatThrownBy(() -> new TokenCipher(OTHER_KEY).decrypt(encrypted))
                .isInstanceOf(YouTubeException.class)
                .hasMessageContaining("encryption key");
    }

    @Test
    void corruptValuesAreRejected() {
        TokenCipher cipher = new TokenCipher(KEY);
        assertThatThrownBy(() -> cipher.decrypt("AAAA")).isInstanceOf(YouTubeException.class);
        assertThatThrownBy(() -> cipher.decrypt("not base64!")).isInstanceOf(YouTubeException.class);
    }

    @Test
    void missingKeyIsNotConfigured() {
        TokenCipher cipher = new TokenCipher("");
        assertThat(cipher.isConfigured()).isFalse();
        assertThat(new TokenCipher(null).isConfigured()).isFalse();
        assertThatThrownBy(() -> cipher.encrypt("x")).isInstanceOf(YouTubeException.class);
        assertThatThrownBy(() -> cipher.decrypt("x")).isInstanceOf(YouTubeException.class);
    }

    @Test
    void shortKeyFailsFast() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new TokenCipher(shortKey)).isInstanceOf(IllegalArgumentException.class);
    }
}
