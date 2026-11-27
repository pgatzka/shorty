package io.github.pgatzka.shorty.render.domain;

/** Visual styles; publishing rotates through them for variety. */
public enum VisualStyle {
    CLASSIC_BARS("Classic bars"),
    RAINBOW_BARS("Rainbow bars"),
    SCATTER_DOTS("Scatter dots"),
    MIRRORED_BARS("Mirrored bars");

    private final String displayName;

    VisualStyle(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** The style after this one, wrapping around. */
    public VisualStyle next() {
        VisualStyle[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
