package io.github.pgatzka.shorty.render.service;

import io.github.pgatzka.shorty.render.domain.VisualStyle;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import org.springframework.stereotype.Component;

/** Bars growing up and down from the vertical center of the data area. */
@Component
class MirroredBarsPainter implements StylePainter {

    private static final Color BAR = new Color(0xB794F6);

    @Override
    public VisualStyle style() {
        return VisualStyle.MIRRORED_BARS;
    }

    @Override
    public void paint(Graphics2D graphics, ReplayState state, Rectangle area) {
        int count = state.size();
        int center = area.y + area.height / 2;
        for (int i = 0; i < count; i++) {
            int x = StylePainter.slotX(area, i, count);
            int width = Math.max(1, StylePainter.slotX(area, i + 1, count) - x - ClassicBarsPainter.gap(area, count));
            int half = Math.max(1, (int) Math.round(state.ratio(i) * area.height / 2));
            graphics.setColor(StylePainter.colorFor(state.highlight(i), BAR));
            graphics.fillRect(x, center - half, width, half * 2);
        }
    }
}
