package io.github.pgatzka.shorty.render.service;

/** Rendering or encoding failed. */
public class RenderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RenderException(String message) {
        super(message);
    }

    public RenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
