package com.foldermania.service;

import com.foldermania.icon.FolderFill;
import com.foldermania.icon.FolderIconRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/** Stores the style chosen for a folder inside the folder itself, so it can be detected and edited later. */
public final class StyleStore {

    public static final String STYLE_NAME = ".foldermania.properties";
    public static final String OVERLAY_NAME = ".foldermania-overlay.png";

    public record Style(FolderFill fill, double overlayScale, BufferedImage overlay) {}

    private StyleStore() {}

    public static void write(Path folder, FolderFill fill, double overlayScale, BufferedImage overlay)
            throws IOException {
        Path styleFile = folder.resolve(STYLE_NAME);
        Path overlayFile = folder.resolve(OVERLAY_NAME);
        FileAttrs.unhide(styleFile);
        FileAttrs.unhide(overlayFile);

        Properties props = new Properties();
        props.setProperty("mode", fill.mode().name());
        props.setProperty("color", FolderFill.toHex(fill.primary()));
        props.setProperty("color2", FolderFill.toHex(fill.secondary()));
        props.setProperty("overlayScale", Double.toString(overlayScale));
        try (OutputStream out = Files.newOutputStream(styleFile)) {
            props.store(out, null);
        }
        FileAttrs.hide(styleFile);

        if (overlay != null) {
            if (!ImageIO.write(overlay, "png", overlayFile.toFile())) {
                throw new IOException("Cannot encode the overlay image as PNG");
            }
            FileAttrs.hide(overlayFile);
        } else {
            Files.deleteIfExists(overlayFile);
        }
    }

    /** Returns the stored style of a folder, or empty if it has none. */
    public static Optional<Style> read(Path folder) {
        Path styleFile = folder.resolve(STYLE_NAME);
        if (!Files.exists(styleFile)) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(styleFile)) {
            Properties props = new Properties();
            props.load(in);
            String color = props.getProperty("color");
            if (color == null) {
                return Optional.empty();
            }
            // Files written by older versions have no mode: they are plain one-color folders
            FolderFill.Mode mode;
            try {
                mode = FolderFill.Mode.valueOf(props.getProperty("mode", "SOLID"));
            } catch (IllegalArgumentException e) {
                mode = FolderFill.Mode.SOLID;
            }
            java.awt.Color primary = FolderFill.parseHex(color, FolderIconRenderer.DEFAULT_COLOR);
            java.awt.Color secondary = FolderFill.parseHex(props.getProperty("color2"), primary);
            double scale = Double.parseDouble(props.getProperty("overlayScale", "0.6"));
            Path overlayFile = folder.resolve(OVERLAY_NAME);
            BufferedImage overlay = Files.exists(overlayFile) ? ImageIO.read(overlayFile.toFile()) : null;
            return Optional.of(new Style(new FolderFill(mode, primary, secondary), scale, overlay));
        } catch (IOException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static void delete(Path folder) throws IOException {
        for (String name : List.of(STYLE_NAME, OVERLAY_NAME)) {
            Path file = folder.resolve(name);
            FileAttrs.unhide(file);
            Files.deleteIfExists(file);
        }
    }
}
