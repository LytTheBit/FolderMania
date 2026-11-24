package com.foldermania.icon;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** Draws a Windows 11 style folder, tinted with any color, with an optional overlay icon. */
public final class FolderIconRenderer {

    /** Default Windows-like yellow. */
    public static final Color DEFAULT_COLOR = new Color(0xFFC83D);

    private static final int[] ICO_SIZES = {16, 24, 32, 48, 64, 128, 256};
    private static final double BASE = 256.0; // drawing coordinate space

    private FolderIconRenderer() {}

    /** Renders a solid-color folder. Overlay may be null. */
    public static BufferedImage render(int size, Color base, BufferedImage overlay, double overlayScale) {
        return render(size, FolderFill.solid(base), overlay, overlayScale);
    }

    /** Renders one square image of the given size. Overlay may be null. */
    public static BufferedImage render(int size, FolderFill fill, BufferedImage overlay, double overlayScale) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.transform(AffineTransform.getScaleInstance(size / BASE, size / BASE));

        // Soft drop shadow under the whole folder (a few translucent layers fake a blur)
        for (int i = 3; i >= 1; i--) {
            g.setColor(new Color(0, 0, 0, 10));
            g.fill(new RoundRectangle2D.Double(20 - i, 84 + i * 1.5, 216 + 2 * i, 132 + i, 22, 22));
        }

        // Back panel = body + tab with the sloped edge Windows 11 uses
        Path2D.Double tab = new Path2D.Double();
        tab.moveTo(20, 100);
        tab.lineTo(20, 60);
        tab.quadTo(20, 42, 38, 42);
        tab.lineTo(88, 42);
        tab.curveTo(99, 42, 105, 48, 111, 58);
        tab.lineTo(116, 66);
        tab.lineTo(116, 100);
        tab.closePath();
        Area back = new Area(new RoundRectangle2D.Double(20, 62, 216, 148, 20, 20));
        back.add(new Area(tab));
        fillShape(g, back, fill, FolderIconRenderer::backColor);

        // Front panel: the fill, then a white veil that is strongest at the top and fades out.
        // Dark colors get a gentler veil, otherwise a black folder would fade to grey.
        RoundRectangle2D front = new RoundRectangle2D.Double(20, 80, 216, 136, 20, 20);
        fillShape(g, front, fill, UnaryOperator.identity());
        float brightness = fill.isTwoColor()
                ? (brightness(fill.primary()) + brightness(fill.secondary())) / 2f
                : brightness(fill.primary());
        float lift = 0.12f + 0.26f * brightness;
        g.setPaint(new GradientPaint(0, 80, new Color(1f, 1f, 1f, lift), 0, 216, new Color(1f, 1f, 1f, 0f)));
        g.fill(front);

        // Soft highlight on the top edge of the front panel
        g.setColor(new Color(255, 255, 255, 110));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(36, 82, 220, 82);

        // Overlay icon centered on the front panel
        if (overlay != null) {
            double box = 132 * overlayScale;
            double ratio = (double) overlay.getWidth() / overlay.getHeight();
            double w = ratio >= 1 ? box : box * ratio;
            double h = ratio >= 1 ? box / ratio : box;
            double x = 128 - w / 2;
            double y = 148 - h / 2;
            g.drawImage(overlay, (int) Math.round(x), (int) Math.round(y),
                    (int) Math.round(w), (int) Math.round(h), null);
        }

        g.dispose();
        return img;
    }

    /**
     * Fills a shape according to the fill mode. {@code tone} adjusts each color first
     * (used to make the back panel a bit deeper than the front one).
     */
    private static void fillShape(Graphics2D g, Shape shape, FolderFill fill, UnaryOperator<Color> tone) {
        Color c1 = tone.apply(fill.primary());
        Color c2 = tone.apply(fill.secondary());
        switch (fill.mode()) {
            case SOLID -> {
                g.setColor(c1);
                g.fill(shape);
            }
            case GRADIENT -> {
                // Runs along the diagonal of the folder, top-left to bottom-right
                g.setPaint(new GradientPaint(30, 50, c1, 226, 216, c2));
                g.fill(shape);
            }
            case SPLIT -> {
                Shape oldClip = g.getClip();
                g.clip(new Rectangle2D.Double(0, 0, 128, BASE));
                g.setColor(c1);
                g.fill(shape);
                g.setClip(oldClip);

                g.clip(new Rectangle2D.Double(128, 0, 128, BASE));
                g.setColor(c2);
                g.fill(shape);
                g.setClip(oldClip);
            }
        }
    }

    /** Builds a multi-size .ico file ready to be used by FolderCustomizer. */
    public static void createIco(Path out, Color base, BufferedImage overlay) throws IOException {
        createIco(out, FolderFill.solid(base), overlay, 0.6);
    }

    /** Same as above, with any fill and overlay size. */
    public static void createIco(Path out, FolderFill fill, BufferedImage overlay, double overlayScale)
            throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        for (int size : ICO_SIZES) {
            images.add(render(size, fill, overlay, overlayScale));
        }
        IcoWriter.write(out, images);
    }

    /** The back panel: same hue as the base, a bit deeper and more saturated, like the Windows folder. */
    private static Color backColor(Color c) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        // Near-grey colors (white, grey, black) have no saturation to boost: only darken them
        float sat = hsb[1] < 0.08f ? hsb[1] : Math.min(1f, hsb[1] * 1.18f);
        return Color.getHSBColor(hsb[0], sat, hsb[2] * 0.88f);
    }

    private static float brightness(Color c) {
        return Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null)[2];
    }
}
