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

    public List<String> recentColors() {
        List<String> result = new ArrayList<>();
        for (String s : props.getProperty("recentColors", "").split(",")) {
            if (!s.isBlank()) {
                result.add(s.trim());
            }
        }
        return result;
    }

    public void addRecentColor(String hex) {
        List<String> list = recentColors();
        list.removeIf(c -> c.equalsIgnoreCase(hex));
        list.add(0, hex);
        while (list.size() > MAX_RECENT) {
            list.remove(list.size() - 1);
        }
        props.setProperty("recentColors", String.join(",", list));
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
