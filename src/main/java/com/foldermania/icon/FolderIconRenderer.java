package com.foldermania.icon;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Draws a Windows 11 style folder, tinted with any color, with an optional overlay icon. */
public final class FolderIconRenderer {

    /** Default Windows-like yellow. */
    public static final Color DEFAULT_COLOR = new Color(0xFFC83D);

    private static final int[] ICO_SIZES = {16, 24, 32, 48, 64, 128, 256};
    private static final double BASE = 256.0; // drawing coordinate space

    private FolderIconRenderer() {}

    /** Renders one square image of the given size. Overlay may be null. */
    public static BufferedImage render(int size, Color base, BufferedImage overlay, double overlayScale) {
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
        g.setColor(backColor(base));
        g.fill(back);

        // Front panel: lighter at the top, the base color at the bottom
        RoundRectangle2D front = new RoundRectangle2D.Double(20, 80, 216, 136, 20, 20);
        // Dark colors get a gentler gradient, otherwise a black folder would fade to grey
        float lift = 0.12f + 0.26f * brightness(base);
        g.setPaint(new GradientPaint(0, 80, mix(base, Color.WHITE, lift), 0, 216, base));
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

    /** Builds a multi-size .ico file ready to be used by FolderCustomizer. */
    public static void createIco(Path out, Color base, BufferedImage overlay) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        for (int size : ICO_SIZES) {
            images.add(render(size, base, overlay, 0.6));
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

    /** Linear blend of two colors: t = 0 gives a, t = 1 gives b. */
    private static Color mix(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
