package com.foldermania.icon;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;
import com.github.weisj.jsvg.geometry.size.FloatSize;
import com.github.weisj.jsvg.parser.SVGLoader;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Loads an overlay image from a raster file (PNG, JPG, GIF...) or from an SVG file. */
public final class OverlayImageLoader {

    /** Longest side, in pixels, of a rasterized SVG. Large enough to stay sharp at 256 px icons. */
    private static final int SVG_RASTER_SIZE = 512;

    private OverlayImageLoader() {}

    public static boolean isSvg(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".svg");
    }

    /** Reads the file as a BufferedImage with transparency. Throws IOException if it cannot be decoded. */
    public static BufferedImage load(Path file) throws IOException {
        if (isSvg(file)) {
            return loadSvg(file);
        }
        BufferedImage img = ImageIO.read(file.toFile());
        if (img == null) {
            throw new IOException("Unsupported image format");
        }
        return img;
    }

    private static BufferedImage loadSvg(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return rasterize(in, SVG_RASTER_SIZE);
        }
    }

    /** Rasterizes SVG source text, fitting its longest side to rasterSize pixels. */
    public static BufferedImage fromSvgText(String svg, int rasterSize) throws IOException {
        return rasterize(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)), rasterSize);
    }

    private static BufferedImage rasterize(InputStream in, int rasterSize) throws IOException {
        SVGDocument document = new SVGLoader().load(in);
        if (document == null) {
            throw new IOException("Invalid SVG file");
        }

        // Keep the aspect ratio of the drawing, fitting its longest side to rasterSize
        FloatSize size = document.size();
        float w = size.width > 0 ? size.width : 1f;
        float h = size.height > 0 ? size.height : 1f;
        float factor = rasterSize / Math.max(w, h);
        int width = Math.max(1, Math.round(w * factor));
        int height = Math.max(1, Math.round(h * factor));

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            document.render(null, g, new ViewBox(0, 0, width, height));
        } finally {
            g.dispose();
        }
        return img;
    }
}
