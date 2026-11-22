package com.foldermania.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Helpers to toggle the hidden+system attributes that Windows requires on Folder Mania's files. */
final class FileAttrs {

    private FileAttrs() {}

    static void hide(Path file) throws IOException {
        Files.setAttribute(file, "dos:hidden", true);
        Files.setAttribute(file, "dos:system", true);
    }

    /** Must be called before overwriting or deleting a hidden+system file. */
    static void unhide(Path file) throws IOException {
        if (Files.exists(file)) {
            Files.setAttribute(file, "dos:hidden", false);
            Files.setAttribute(file, "dos:system", false);
        }
    }
}
