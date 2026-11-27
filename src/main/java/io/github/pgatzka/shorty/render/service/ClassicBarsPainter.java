package io.github.pgatzka.shorty.render.service;

import io.github.pgatzka.shorty.render.domain.VisualStyle;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import org.springframework.stereotype.Component;

/** White bars standing on the bottom of the data area. */
@Component
class ClassicBarsPainter implements StylePainter {

    private static final Color BAR = new Color(0xF2F2F2);

    @Override
    public VisualStyle style() {
        return VisualStyle.CLASSIC_BARS;
    }

    @Override
    public void paint(Graphics2D graphics, ReplayState state, Rectangle area) {
        int count = state.size();
        int bottom = area.y + area.height;
        for (int i = 0; i < count; i++) {
            int x = StylePainter.slotX(area, i, count);
            int width = Math.max(1, StylePainter.slotX(area, i + 1, count) - x - gap(area, count));
            int height = Math.max(1, (int) Math.round(state.ratio(i) * area.height));
            graphics.setColor(StylePainter.colorFor(state.highlight(i), BAR));
            graphics.fillRect(x, bottom - height, width, height);
        }
    }

    static int gap(Rectangle area, int count) {
        return area.width / count >= 4 ? 1 : 0;
    }
}
