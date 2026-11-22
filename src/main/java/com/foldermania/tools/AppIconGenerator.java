package com.foldermania.tools;

import com.foldermania.icon.FolderIconRenderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** One-off tool: generates the application's own .ico file. */
public final class AppIconGenerator {

    private AppIconGenerator() {}

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "src/main/resources/app.ico");
        Files.createDirectories(out.toAbsolutePath().getParent());
        FolderIconRenderer.createIco(out, FolderIconRenderer.DEFAULT_COLOR, null);
        System.out.println("Written " + out.toAbsolutePath());
    }
}
