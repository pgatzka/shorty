package io.github.pgatzka.shorty.render.service;

import io.github.pgatzka.shorty.render.domain.VisualStyle;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import org.springframework.stereotype.Component;

/** One dot per value; sorted data forms a diagonal line. */
@Component
class ScatterDotsPainter implements StylePainter {

    private static final Color DOT = new Color(0x7FD6FF);

    @Override
    public VisualStyle style() {
        return VisualStyle.SCATTER_DOTS;
    }

    @Override
    public void paint(Graphics2D graphics, ReplayState state, Rectangle area) {
        int count = state.size();
        int slot = Math.max(1, area.width / count);
        int diameter = Math.clamp(slot, 3, 24);
        int bottom = area.y + area.height;
        for (int i = 0; i < count; i++) {
            int centerX = StylePainter.slotX(area, i, count) + slot / 2;
            int centerY = bottom - (int) Math.round(state.ratio(i) * (area.height - diameter)) - diameter / 2;
            graphics.setColor(StylePainter.colorFor(state.highlight(i), DOT));
            graphics.fillOval(centerX - diameter / 2, centerY - diameter / 2, diameter, diameter);
        }
    }
}
