package io.github.pgatzka.shorty.render.service;

import io.github.pgatzka.shorty.render.domain.VisualStyle;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/** Draws the data (bars, dots, ...) of one visual style into the data area of a frame. */
interface StylePainter {

    Color COMPARE = new Color(0xFF4D4D);
    Color CHANGE = new Color(0xFFD23F);
    Color SORTED = new Color(0x3DDC84);

    VisualStyle style();

    void paint(Graphics2D graphics, ReplayState state, Rectangle area);

    /** Color for a highlighted bar, or {@code base} when not highlighted. */
    static Color colorFor(ReplayState.Highlight highlight, Color base) {
        return switch (highlight) {
            case COMPARE -> COMPARE;
            case CHANGE -> CHANGE;
            case SORTED -> SORTED;
            case NONE -> base;
        };
    }

    /** Left edge of slot {@code index} when {@code count} slots share {@code area}'s width. */
    static int slotX(Rectangle area, int index, int count) {
        return area.x + (int) Math.round((double) index * area.width / count);
    }
}
