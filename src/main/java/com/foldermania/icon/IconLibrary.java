package com.foldermania.icon;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * The built-in icon library: the Tabler Icons set (MIT), bundled under /icons/tabler/.
 * <p>
 * The set is described by index.tsv (name, category, tags, available styles), so nothing has to be
 * listed from inside the jar. popular.txt holds the curated "most used" icons, most useful first.
 */
public final class IconLibrary {

    public static final String POPULAR = "Popular";
    public static final String ALL = "All";

    public enum Style {
        OUTLINE("outline"),
        FILLED("filled");

        private final String folder;

        Style(String folder) {
            this.folder = folder;
        }
    }

    /** One icon of the library. {@code haystack} is the lowercase text the search runs on. */
    public record Entry(String name, String category, String haystack, boolean outline, boolean filled) {

        public boolean has(Style style) {
            return style == Style.OUTLINE ? outline : filled;
        }

        /** Human readable name, e.g. "device gamepad 2". */
        public String label() {
            return name.replace('-', ' ');
        }
    }

    private static final String ROOT = "/icons/tabler/";

    private static IconLibrary instance;

    private final List<Entry> all;
    private final List<Entry> popular;
    private final List<String> categories;

    private IconLibrary() throws IOException {
        Map<String, Entry> byName = new LinkedHashMap<>();
        TreeSet<String> cats = new TreeSet<>();

        try (BufferedReader reader = open("index.tsv")) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t", -1);
                if (parts.length < 4) {
                    continue;
                }
                String name = parts[0];
                String category = parts[1];
                String tags = parts[2];
                String styles = parts[3];
                String haystack = (name + "," + tags).toLowerCase(Locale.ROOT);
                byName.put(name, new Entry(name, category, haystack,
                        styles.contains("o"), styles.contains("f")));
                cats.add(category);
            }
        }

        List<Entry> pop = new ArrayList<>();
        try (BufferedReader reader = open("popular.txt")) {
            String line;
            while ((line = reader.readLine()) != null) {
                Entry entry = byName.get(line.trim());
                if (entry != null) {
                    pop.add(entry);
                }
            }
        }

        this.all = List.copyOf(byName.values());
        this.popular = List.copyOf(pop);
        this.categories = List.copyOf(cats);
    }

    public static synchronized IconLibrary get() {
        if (instance == null) {
            try {
                instance = new IconLibrary();
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot load the icon library", e);
            }
        }
        return instance;
    }

    public List<Entry> popular() {
        return popular;
    }

    /** Category names such as "Document" or "Food", sorted alphabetically. */
    public List<String> categories() {
        return categories;
    }

    public int size() {
        return all.size();
    }

    /**
     * Finds icons. Every word of the query must appear in the icon's name or tags.
     *
     * @param category POPULAR, ALL (or null) or a category name
     */
    public List<Entry> search(String query, String category, Style style) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String[] tokens = q.isEmpty() ? new String[0] : q.split("\\s+");
        List<Entry> source = POPULAR.equals(category) ? popular : all;
        boolean byCategory = category != null && !ALL.equals(category) && !POPULAR.equals(category);

        List<Entry> result = new ArrayList<>();
        for (Entry entry : source) {
            if (!entry.has(style)) {
                continue;
            }
            if (byCategory && !entry.category().equals(category)) {
                continue;
            }
            if (matches(entry, tokens)) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Renders an icon to a transparent image.
     *
     * @param colorHex color for the strokes or fill, as #RRGGBB (Tabler icons use currentColor)
     * @param size     longest side in pixels
     */
    public BufferedImage render(Entry entry, Style style, String colorHex, int size) throws IOException {
        String path = ROOT + style.folder + "/" + entry.name() + ".svg";
        String svg;
        try (InputStream in = IconLibrary.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Icon not found: " + entry.name() + " (" + style.folder + ")");
            }
            svg = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return OverlayImageLoader.fromSvgText(svg.replace("currentColor", colorHex), size);
    }

    private static boolean matches(Entry entry, String[] tokens) {
        for (String token : tokens) {
            if (!entry.haystack().contains(token)) {
                return false;
            }
        }
        return true;
    }

    private static BufferedReader open(String file) throws IOException {
        InputStream in = IconLibrary.class.getResourceAsStream(ROOT + file);
        if (in == null) {
            throw new IOException("Missing resource: " + ROOT + file);
        }
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
}
