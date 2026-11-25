package com.foldermania.settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/** Persistent user settings stored in %APPDATA%\FolderMania. */
public final class AppSettings {

    private static final int MAX_RECENT = 8;

    private final Path dir;
    private final Path file;
    private final Properties props = new Properties();

    public AppSettings() {
        String appData = System.getenv("APPDATA");
        Path base = appData != null ? Path.of(appData) : Path.of(System.getProperty("user.home"));
        dir = base.resolve("FolderMania");
        file = dir.resolve("settings.properties");
        load();
    }

    /** Directory holding settings, presets and rules. */
    public Path dir() {
        return dir;
    }

    /** Recent colors used for folders. */
    public List<String> recentColors() {
        return recentColors("recentColors");
    }

    /** Recent colors used for the overlay icon. */
    public List<String> recentIconColors() {
        return recentColors("recentIconColors");
    }

    public void addRecentColor(String hex) {
        addRecent("recentColors", hex);
    }

    public void addRecentIconColor(String hex) {
        addRecent("recentIconColors", hex);
    }

    /** Language code (en, it, fr, es), or null if the user never chose one. */
    public String language() {
        return props.getProperty("language");
    }

    public void setLanguage(String code) {
        props.setProperty("language", code);
        save();
    }

    private List<String> recentColors(String key) {
        List<String> result = new ArrayList<>();
        for (String s : props.getProperty(key, "").split(",")) {
            if (!s.isBlank()) {
                result.add(s.trim());
            }
        }
        return result;
    }

    private void addRecent(String key, String hex) {
        List<String> list = recentColors(key);
        list.removeIf(c -> c.equalsIgnoreCase(hex));
        list.add(0, hex);
        while (list.size() > MAX_RECENT) {
            list.remove(list.size() - 1);
        }
        props.setProperty(key, String.join(",", list));
        save();
    }

    /** Deletes the whole settings directory (used by the clean uninstall). */
    public void deleteAll() throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(dir)) {
            paths = walk.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path p : paths) {
            Files.delete(p);
        }
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException ignored) {
            // Settings are optional: fall back to defaults
        }
    }

    private void save() {
        try {
            Files.createDirectories(dir);
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Folder Mania settings");
            }
        } catch (IOException ignored) {
            // Not critical
        }
    }
}
