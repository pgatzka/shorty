package io.github.pgatzka.shorty.render.service;

import io.github.pgatzka.shorty.render.domain.VisualStyle;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import org.springframework.stereotype.Component;

/** Bars colored by value along the hue wheel; highlights are drawn as a white cap. */
@Component
class RainbowBarsPainter implements StylePainter {

    private static final float HUE_RANGE = 0.8f;

    @Override
    public VisualStyle style() {
        return VisualStyle.RAINBOW_BARS;
    }

    @Override
    public void paint(Graphics2D graphics, ReplayState state, Rectangle area) {
        int count = state.size();
        int bottom = area.y + area.height;
        int capHeight = Math.max(2, area.height / 80);
        for (int i = 0; i < count; i++) {
            int x = StylePainter.slotX(area, i, count);
            int width = Math.max(1, StylePainter.slotX(area, i + 1, count) - x - ClassicBarsPainter.gap(area, count));
            int height = Math.max(1, (int) Math.round(state.ratio(i) * area.height));
            graphics.setColor(Color.getHSBColor((float) state.ratio(i) * HUE_RANGE, 0.85f, 1.0f));
            graphics.fillRect(x, bottom - height, width, height);
            ReplayState.Highlight highlight = state.highlight(i);
            if (highlight != ReplayState.Highlight.NONE) {
                graphics.setColor(highlight == ReplayState.Highlight.SORTED ? SORTED : Color.WHITE);
                graphics.fillRect(x, bottom - height, width, Math.min(height, capHeight));
            }
        }
    }
}
