package io.github.pgatzka.shorty.render.service;

import io.github.pgatzka.shorty.render.domain.VisualStyle;
import io.github.pgatzka.shorty.sorting.domain.SortAlgorithm;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Draws a complete frame: background, data in the chosen style, and the text overlays. */
@Component
class FrameComposer {

    /** Phase of the video a frame belongs to. */
    enum Phase {
        INTRO,
        SORTING,
        SWEEP,
        OUTRO
    }

    /** Everything about a frame that is not in the replay state. */
    record Overlay(SortAlgorithm algorithm, VisualStyle style, String channelHandle, Phase phase, double progress) {}

    /** Layout is designed for a 1080 px wide frame and scaled from there. */
    private static final double DESIGN_WIDTH = 1080.0;

    private static final Color BACKGROUND = new Color(0x101418);
    private static final Color TEXT = new Color(0xF5F5F5);
    private static final Color MUTED = new Color(0x9AA4AE);

    private final Map<VisualStyle, StylePainter> painters = new EnumMap<>(VisualStyle.class);

    FrameComposer(List<StylePainter> painters) {
        painters.forEach(painter -> this.painters.put(painter.style(), painter));
    }

    void compose(BufferedImage image, ReplayState state, Overlay overlay) {
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int width = image.getWidth();
            int height = image.getHeight();
            double scale = width / DESIGN_WIDTH;

            graphics.setColor(BACKGROUND);
            graphics.fillRect(0, 0, width, height);

            painterFor(overlay.style()).paint(graphics, state, dataArea(width, height, scale));
            drawHeader(graphics, state, overlay, width, scale);
            drawFooter(graphics, overlay.channelHandle(), width, height, scale);
            if (overlay.phase() == Phase.OUTRO) {
                drawOutro(graphics, overlay, width, height, scale);
            }
        } finally {
            graphics.dispose();
        }
    }

    static Rectangle dataArea(int width, int height, double scale) {
        int margin = (int) Math.round(40 * scale);
        int top = (int) Math.round(640 * scale);
        int bottom = height - (int) Math.round(260 * scale);
        return new Rectangle(margin, top, width - 2 * margin, Math.max(1, bottom - top));
    }

    private StylePainter painterFor(VisualStyle style) {
        StylePainter painter = painters.get(style);
        if (painter == null) {
            throw new RenderException("No painter for style " + style);
        }
        return painter;
    }

    private static void drawHeader(Graphics2D graphics, ReplayState state, Overlay overlay, int width, double scale) {
        graphics.setColor(TEXT);
        graphics.setFont(font(Font.BOLD, 96, scale));
        drawCentered(graphics, overlay.algorithm().displayName(), width, (int) Math.round(210 * scale));

        graphics.setColor(MUTED);
        graphics.setFont(font(Font.PLAIN, 46, scale));
        String subtitle = state.size() + " bars  ·  " + overlay.algorithm().bigO();
        drawCentered(graphics, subtitle, width, (int) Math.round(300 * scale));

        graphics.setFont(font(Font.BOLD, 44, scale));
        graphics.setColor(TEXT);
        int y = (int) Math.round(420 * scale);
        int step = (int) Math.round(62 * scale);
        drawCentered(graphics, "Comparisons  " + number(state.comparisons()), width, y);
        drawCentered(graphics, "Swaps  " + number(state.swaps()), width, y + step);
        drawCentered(graphics, "Array accesses  " + number(state.accesses()), width, y + 2 * step);
    }

    private static void drawFooter(Graphics2D graphics, String handle, int width, int height, double scale) {
        if (handle.isEmpty()) {
            return;
        }
        graphics.setColor(MUTED);
        graphics.setFont(font(Font.PLAIN, 40, scale));
        drawCentered(graphics, handle, width, height - (int) Math.round(130 * scale));
    }

    private static void drawOutro(Graphics2D graphics, Overlay overlay, int width, int height, double scale) {
        float alpha = (float) Math.clamp(overlay.progress() * 3.0, 0.0, 1.0);
        Composite previous = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.85f));
        graphics.setColor(Color.BLACK);
        graphics.fillRect(0, 0, width, height);
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        graphics.setColor(StylePainter.SORTED);
        graphics.setFont(font(Font.BOLD, 120, scale));
        drawCentered(graphics, "Sorted!", width, height / 2 - (int) Math.round(80 * scale));

        graphics.setColor(TEXT);
        graphics.setFont(font(Font.BOLD, 56, scale));
        String handle = overlay.channelHandle();
        String line = handle.isEmpty() ? "Follow for more sorting!" : "Follow " + handle;
        drawCentered(graphics, line, width, height / 2 + (int) Math.round(60 * scale));
        graphics.setFont(font(Font.PLAIN, 44, scale));
        graphics.setColor(MUTED);
        drawCentered(graphics, "for a new algorithm every day", width, height / 2 + (int) Math.round(140 * scale));
        graphics.setComposite(previous);
    }

    private static Font font(int style, int designSize, double scale) {
        return new Font(Font.SANS_SERIF, style, Math.max(6, (int) Math.round(designSize * scale)));
    }

    private static void drawCentered(Graphics2D graphics, String text, int width, int baseline) {
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(text, (width - metrics.stringWidth(text)) / 2, baseline);
    }

    private static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
