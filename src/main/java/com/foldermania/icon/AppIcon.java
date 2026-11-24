package com.foldermania.icon;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The icon of Folder Mania itself: a violet-to-pink gradient folder with a white palette on it.
 * Used for the window icon, the About dialog and the generated app.ico.
 */
public final class AppIcon {

    public static final FolderFill FILL =
            FolderFill.gradient(new Color(0x6D5DF6), new Color(0xF0408F));

    private static final String ICON_NAME = "palette";
    private static final double OVERLAY_SCALE = 0.62;
    private static final Color OVERLAY_COLOR = Color.WHITE;

    private static BufferedImage overlay;

    private AppIcon() {}

    /** Renders the app icon at the given size (in pixels). */
    public static BufferedImage render(int size) {
        return FolderIconRenderer.render(size, FILL, overlay(), OVERLAY_SCALE);
    }

    /** Writes a multi-size .ico file with the app icon. */
    public static void createIco(Path out) throws IOException {
        FolderIconRenderer.createIco(out, FILL, overlay(), OVERLAY_SCALE);
    }

    /** The palette symbol in white, or null if the library cannot be read (the folder is then plain). */
    private static synchronized BufferedImage overlay() {
        if (overlay == null) {
            try {
                IconLibrary library = IconLibrary.get();
                IconLibrary.Entry entry = library.find(ICON_NAME).orElse(null);
                if (entry != null) {
                    overlay = library.render(entry, IconLibrary.Style.OUTLINE, "#FFFFFF", 512);
                }
            } catch (IOException | RuntimeException e) {
                overlay = null;
            }
        }
        return overlay;
    }
}
