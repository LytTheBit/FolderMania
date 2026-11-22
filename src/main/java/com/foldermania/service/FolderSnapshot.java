package com.foldermania.service;

import com.foldermania.shell.FolderCustomizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Captures every file Folder Mania may touch in a folder, so a change can be undone exactly. */
public final class FolderSnapshot {

    private static final List<String> TRACKED = List.of(
            "desktop.ini", FolderService.ICON_NAME, StyleStore.STYLE_NAME, StyleStore.OVERLAY_NAME);

    private final Path folder;
    private final boolean wasReadOnly;
    private final Map<String, byte[]> files = new HashMap<>();

    private FolderSnapshot(Path folder, boolean wasReadOnly) {
        this.folder = folder;
        this.wasReadOnly = wasReadOnly;
    }

    public static FolderSnapshot capture(Path folder) throws IOException {
        boolean readOnly = (Boolean) Files.getAttribute(folder, "dos:readonly");
        FolderSnapshot snapshot = new FolderSnapshot(folder, readOnly);
        for (String name : TRACKED) {
            Path file = folder.resolve(name);
            if (Files.exists(file)) {
                snapshot.files.put(name, Files.readAllBytes(file));
            }
        }
        return snapshot;
    }

    public Path folder() {
        return folder;
    }

    public void restore() throws IOException {
        for (String name : TRACKED) {
            Path file = folder.resolve(name);
            FileAttrs.unhide(file);
            Files.deleteIfExists(file);

            byte[] data = files.get(name);
            if (data != null) {
                Files.write(file, data);
                FileAttrs.hide(file);
            }
        }
        Files.setAttribute(folder, "dos:readonly", wasReadOnly);
        FolderCustomizer.refresh(folder);
    }
}
