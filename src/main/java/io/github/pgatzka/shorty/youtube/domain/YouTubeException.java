package io.github.pgatzka.shorty.youtube.domain;

/** A YouTube operation failed; the message is suitable for the UI and upload history. */
public class YouTubeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public YouTubeException(String message) {
        super(message);
    }

    public YouTubeException(String message, Throwable cause) {
        super(message, cause);
    }
}
