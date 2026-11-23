package com.foldermania.shell;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal editor for a desktop.ini file.
 * <p>
 * It only touches the keys it is asked to change: every other line (other sections, comments,
 * keys such as LocalizedResourceName or InfoTip, blank lines) is kept exactly as it was.
 * The original text encoding is preserved whenever the new content can still be represented in it.
 */
public final class DesktopIni {

    private static final Charset ANSI = Charset.forName("windows-1252");

    private final List<String> lines;
    private Charset charset;
    private boolean bom;

    private DesktopIni(List<String> lines, Charset charset, boolean bom) {
        this.lines = lines;
        this.charset = charset;
        this.bom = bom;
    }

    /** A new, empty file. Explorer prefers UTF-16 LE with BOM, so that is the default. */
    public static DesktopIni empty() {
        return new DesktopIni(new ArrayList<>(), StandardCharsets.UTF_16LE, true);
    }

    /** Reads an existing file, detecting its encoding from the BOM (no BOM means ANSI, as Windows does). */
    public static DesktopIni read(Path file) throws IOException {
        byte[] data = Files.readAllBytes(file);
        Charset cs = ANSI;
        int skip = 0;
        boolean hasBom = true;

        if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE) {
            cs = StandardCharsets.UTF_16LE;
            skip = 2;
        } else if (data.length >= 2 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF) {
            cs = StandardCharsets.UTF_16BE;
            skip = 2;
        } else if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB
                && (data[2] & 0xFF) == 0xBF) {
            cs = StandardCharsets.UTF_8;
            skip = 3;
        } else {
            hasBom = false;
        }

        String text = new String(data, skip, data.length - skip, cs);
        List<String> result = new ArrayList<>(List.of(text.split("\\r?\\n", -1)));
        // A trailing line break produces one empty last element: drop it, write() adds it back
        if (!result.isEmpty() && result.get(result.size() - 1).isEmpty()) {
            result.remove(result.size() - 1);
        }
        return new DesktopIni(result, cs, hasBom);
    }

    /** Returns the value of a key (case-insensitive), or null if the section or key is missing. */
    public String get(String section, String key) {
        int[] range = sectionRange(section);
        if (range == null) {
            return null;
        }
        int index = findKey(range, key);
        if (index < 0) {
            return null;
        }
        String line = lines.get(index);
        return line.substring(line.indexOf('=') + 1).trim();
    }

    /** Sets a key, creating the section if needed. Existing keys keep their position. */
    public void set(String section, String key, String value) {
        String newLine = key + "=" + value;
        int[] range = sectionRange(section);

        if (range == null) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
                lines.add("");
            }
            lines.add("[" + section + "]");
            lines.add(newLine);
            return;
        }

        int index = findKey(range, key);
        if (index >= 0) {
            lines.set(index, newLine);
            return;
        }

        // Insert after the last non-blank line of the section, so blank separators stay in place
        int insertAt = range[1];
        while (insertAt > range[0] + 1 && lines.get(insertAt - 1).isBlank()) {
            insertAt--;
        }
        lines.add(insertAt, newLine);
    }

    /** Removes a key. Returns true if it was present. */
    public boolean remove(String section, String key) {
        int[] range = sectionRange(section);
        if (range == null) {
            return false;
        }
        int index = findKey(range, key);
        if (index < 0) {
            return false;
        }
        lines.remove(index);
        return true;
    }

    /** True if at least one key=value entry is left (comments, blank lines and bare headers don't count). */
    public boolean hasEntries() {
        for (String line : lines) {
            if (isKeyValue(line)) {
                return true;
            }
        }
        return false;
    }

    /** Writes the file back with CRLF line breaks, in the original encoding when possible. */
    public void write(Path file) throws IOException {
        String text = String.join("\r\n", lines) + "\r\n";

        // An ANSI file cannot hold characters outside its code page (e.g. a path with Cyrillic letters)
        if (!charset.newEncoder().canEncode(text)) {
            charset = StandardCharsets.UTF_16LE;
            bom = true;
        }

        byte[] prefix = new byte[0];
        if (bom) {
            if (charset.equals(StandardCharsets.UTF_16LE)) {
                prefix = new byte[]{(byte) 0xFF, (byte) 0xFE};
            } else if (charset.equals(StandardCharsets.UTF_16BE)) {
                prefix = new byte[]{(byte) 0xFE, (byte) 0xFF};
            } else if (charset.equals(StandardCharsets.UTF_8)) {
                prefix = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            }
        }
        byte[] body = text.getBytes(charset);
        byte[] data = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, data, 0, prefix.length);
        System.arraycopy(body, 0, data, prefix.length, body.length);
        Files.write(file, data);
    }

    // ---------- Internals ----------

    /** Returns {headerIndex, endExclusive} of a section, or null if it does not exist. */
    private int[] sectionRange(String section) {
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            String name = headerName(lines.get(i));
            if (name == null) {
                continue;
            }
            if (start >= 0) {
                return new int[]{start, i};
            }
            if (name.equalsIgnoreCase(section)) {
                start = i;
            }
        }
        return start >= 0 ? new int[]{start, lines.size()} : null;
    }

    private int findKey(int[] range, String key) {
        for (int i = range[0] + 1; i < range[1]; i++) {
            String line = lines.get(i);
            if (isKeyValue(line)
                    && line.substring(0, line.indexOf('=')).trim().equalsIgnoreCase(key)) {
                return i;
            }
        }
        return -1;
    }

    private static String headerName(String line) {
        String t = line.trim();
        if (t.length() >= 2 && t.startsWith("[") && t.endsWith("]")) {
            return t.substring(1, t.length() - 1).trim();
        }
        return null;
    }

    private static boolean isKeyValue(String line) {
        String t = line.trim();
        return !t.isEmpty() && !t.startsWith(";") && headerName(line) == null && t.indexOf('=') > 0;
    }
}
