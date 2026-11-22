package com.foldermania.icon;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
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

        // Back panel = body + tab
        Area back = new Area(new RoundRectangle2D.Double(20, 60, 216, 150, 20, 20));
        back.add(new Area(new RoundRectangle2D.Double(20, 36, 92, 50, 16, 16)));
        g.setColor(shade(base, 0.78f));
        g.fill(back);

        // Front panel with a vertical gradient
        RoundRectangle2D front = new RoundRectangle2D.Double(20, 84, 216, 132, 20, 20);
        g.setPaint(new GradientPaint(0, 84, shade(base, 1.08f), 0, 216, base));
        g.fill(front);

        // Soft highlight on the top edge of the front panel
        g.setColor(new Color(255, 255, 255, 90));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(34, 86, 222, 86);

        // Overlay icon centered on the front panel
        if (overlay != null) {
            double box = 132 * overlayScale;
            double ratio = (double) overlay.getWidth() / overlay.getHeight();
            double w = ratio >= 1 ? box : box * ratio;
            double h = ratio >= 1 ? box / ratio : box;
            double x = 128 - w / 2;
            double y = 150 - h / 2;
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

    /** Multiplies the HSB brightness by the given factor (clamped to 1). */
    private static Color shade(Color c, float factor) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        return Color.getHSBColor(hsb[0], hsb[1], Math.min(1f, hsb[2] * factor));
    }
}
