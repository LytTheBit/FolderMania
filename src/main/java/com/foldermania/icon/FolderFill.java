package com.foldermania.icon;

import java.awt.Color;
import java.util.Locale;

/**
 * How the folder body is colored: one flat color, a diagonal gradient between two colors,
 * or two halves (left and right) with a crisp edge in the middle.
 */
public record FolderFill(Mode mode, Color primary, Color secondary) {

    public enum Mode { SOLID, GRADIENT, SPLIT }

    public FolderFill {
        if (mode == null) {
            mode = Mode.SOLID;
        }
        if (primary == null) {
            primary = FolderIconRenderer.DEFAULT_COLOR;
        }
        if (secondary == null) {
            secondary = primary;
        }
    }

    public static FolderFill solid(Color color) {
        return new FolderFill(Mode.SOLID, color, color);
    }

    public static FolderFill gradient(Color from, Color to) {
        return new FolderFill(Mode.GRADIENT, from, to);
    }

    public static FolderFill split(Color left, Color right) {
        return new FolderFill(Mode.SPLIT, left, right);
    }

    /** True when two different colors are involved. */
    public boolean isTwoColor() {
        return mode != Mode.SOLID;
    }

    /** Parses #RRGGBB (the # is optional). Returns the fallback if the text is not a valid color. */
    public static Color parseHex(String hex, Color fallback) {
        if (hex == null) {
            return fallback;
        }
        String h = hex.trim();
        if (h.startsWith("#")) {
            h = h.substring(1);
        }
        try {
            return h.length() == 6 ? new Color(Integer.parseInt(h, 16)) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static String toHex(Color c) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }
}
