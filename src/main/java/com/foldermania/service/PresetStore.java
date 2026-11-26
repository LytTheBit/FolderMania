package com.foldermania.service;

import com.foldermania.icon.FolderFill;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Named styles (presets) and automatic rules that map folders to presets.
 * Each preset is a directory under presets/ holding the same files {@link StyleStore} writes into folders.
 * Rules live in rules.txt, one per line: {@code pattern = preset}. A rule matches a folder when the
 * folder's own name, or the name of any direct child, matches the glob pattern (e.g. {@code .git},
 * {@code node_modules}, {@code *.pdf}). The first matching rule wins.
 */
public final class PresetStore {

    public record Rule(String pattern, String preset) {
        PathMatcher matcher() {
            return FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        }
    }

    private final Path presetsDir;
    private final Path rulesFile;

    public PresetStore(Path baseDir) {
        presetsDir = baseDir.resolve("presets");
        rulesFile = baseDir.resolve("rules.txt");
    }

    // ---------- Presets ----------

    public List<String> names() {
        if (!Files.isDirectory(presetsDir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(presetsDir)) {
            return s.filter(Files::isDirectory).map(p -> p.getFileName().toString())
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public void save(String name, FolderFill fill, double overlayScale, BufferedImage overlay) throws IOException {
        Path dir = presetDir(name);
        Files.createDirectories(dir);
        StyleStore.write(dir, fill, overlayScale, overlay);
    }

    public Optional<StyleStore.Style> load(String name) {
        try {
            return StyleStore.read(presetDir(name));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public void delete(String name) throws IOException {
        Path dir = presetDir(name);
        StyleStore.delete(dir);
        Files.deleteIfExists(dir);
    }

    /** Rejects names that are not valid single path segments (no traversal outside presets/). */
    private Path presetDir(String name) throws IOException {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.matches(".*[\\\\/:*?\"<>|].*") || n.equals(".") || n.equals("..")) {
            throw new IOException("Invalid preset name: " + name);
        }
        return presetsDir.resolve(n);
    }

    // ---------- Rules ----------

    public List<Rule> rules() {
        List<Rule> rules = new ArrayList<>();
        if (!Files.exists(rulesFile)) {
            return rules;
        }
        try {
            for (String line : Files.readAllLines(rulesFile, StandardCharsets.UTF_8)) {
                parseRule(line).ifPresent(rules::add);
            }
        } catch (IOException ignored) {
            // No rules
        }
        return rules;
    }

    /** Raw text of the rules file, for editing. */
    public String rulesText() {
        try {
            return Files.exists(rulesFile) ? Files.readString(rulesFile, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }

    public void saveRulesText(String text) throws IOException {
        Files.createDirectories(rulesFile.getParent());
        Files.writeString(rulesFile, text, StandardCharsets.UTF_8);
    }

    static Optional<Rule> parseRule(String line) {
        String l = line.strip();
        int eq = l.lastIndexOf('=');
        if (l.isEmpty() || l.startsWith("#") || eq <= 0) {
            return Optional.empty();
        }
        String pattern = l.substring(0, eq).strip();
        String preset = l.substring(eq + 1).strip();
        if (pattern.isEmpty() || preset.isEmpty()) {
            return Optional.empty();
        }
        try {
            Rule rule = new Rule(pattern, preset);
            rule.matcher(); // validate the glob
            return Optional.of(rule);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * For each folder, the style of the first rule that matches it. Folders matching no rule,
     * or whose rule names a missing preset, are left out.
     */
    public Map<Path, StyleStore.Style> match(List<Path> folders) {
        List<Rule> rules = rules();
        Map<String, Optional<StyleStore.Style>> cache = new LinkedHashMap<>();
        List<PathMatcher> matchers = rules.stream().map(Rule::matcher).toList();
        Map<Path, StyleStore.Style> result = new LinkedHashMap<>();
        for (Path folder : folders) {
            List<Path> names = childNames(folder);
            Path own = folder.getFileName();
            if (own != null) {
                names.add(0, own);
            }
            for (int i = 0; i < rules.size(); i++) {
                PathMatcher m = matchers.get(i);
                if (names.stream().anyMatch(m::matches)) {
                    cache.computeIfAbsent(rules.get(i).preset(), this::load)
                            .ifPresent(style -> result.put(folder, style));
                    break;
                }
            }
        }
        return result;
    }

    private static List<Path> childNames(Path folder) {
        List<Path> names = new ArrayList<>();
        try (DirectoryStream<Path> s = Files.newDirectoryStream(folder)) {
            for (Path p : s) {
                names.add(p.getFileName());
            }
        } catch (IOException ignored) {
            // Unreadable folder: match on its own name only
        }
        names.sort(Comparator.naturalOrder());
        return names;
    }
}
