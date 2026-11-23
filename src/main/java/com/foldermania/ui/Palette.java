package com.foldermania.ui;

import java.util.List;

/**
 * The built-in color palette: 11 color families, each with three shades.
 * Families are listed in rainbow order, followed by the neutrals.
 */
public final class Palette {

    /** Shade order of the rows in the grid, from top to bottom. */
    public enum Shade { DARK, CLASSIC, LIGHT }

    /** One column of the grid. */
    public record Family(String name, String dark, String classic, String light) {

        public String hex(Shade shade) {
            return switch (shade) {
                case DARK -> dark;
                case CLASSIC -> classic;
                case LIGHT -> light;
            };
        }
    }

    public static final List<Family> FAMILIES = List.of(
            new Family("Red",    "#A61E22", "#E5484D", "#F4A0A3"),
            new Family("Orange", "#B5470A", "#F76B15", "#FFB070"),
            new Family("Yellow", "#C99200", "#FFC83D", "#FFE08A"),
            new Family("Green",  "#1F7A4F", "#30A46C", "#8FD9B0"),
            new Family("Blue",   "#1D4ED8", "#3B82F6", "#9CC2FF"),
            new Family("Purple", "#6B2FA8", "#8E4EC6", "#C9A5EA"),
            new Family("Pink",   "#BE1E5E", "#E93D82", "#F7A3C4"),
            new Family("Brown",  "#6B4A30", "#AD7F58", "#D4B496"),
            new Family("White",  "#C4C8CF", "#EDEEF0", "#FFFFFF"),
            new Family("Gray",   "#5C5F6A", "#8B8D98", "#C2C4CC"),
            new Family("Black",  "#0F0F10", "#26272B", "#4A4C54"));

    /** The Windows-like yellow, used as the initial folder color. */
    public static final String DEFAULT_HEX = "#FFC83D";

    private Palette() {}
}
