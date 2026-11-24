package com.foldermania.service;

import com.foldermania.icon.FolderFill;
import com.foldermania.icon.FolderIconRenderer;
import com.foldermania.icon.IcoWriter;
import com.foldermania.shell.FolderCustomizer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** High-level operations on one or many folders, with a session-long undo stack. */
public final class FolderService {

    public static final String ICON_NAME = ".foldermania.ico";
    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    /** Outcome of a batch: folders that worked, and folders that failed with the reason. */
    public record BatchResult(List<Path> succeeded, Map<Path, String> failed) {}

    @FunctionalInterface
    private interface IoAction {
        void run(Path folder) throws IOException;
    }

    private final Deque<List<FolderSnapshot>> undoStack = new ArrayDeque<>();

    /** Expands the roots with all their subfolders, skipping hidden and system ones. */
    public static List<Path> expand(List<Path> roots, boolean includeSubfolders) throws IOException {
        Set<Path> result = new LinkedHashSet<>();
        for (Path root : roots) {
            if (!includeSubfolders) {
                result.add(root);
                continue;
            }
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    boolean hiddenOrSystem = attrs instanceof DosFileAttributes dos
                            && (dos.isHidden() || dos.isSystem());
                    if (!dir.equals(root) && hiddenOrSystem) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    result.add(dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE; // e.g. access denied: skip it
                }
            });
        }
        return new ArrayList<>(result);
    }

    public BatchResult applyAll(List<Path> folders, FolderFill fill, BufferedImage overlay, double overlayScale) {
        return runBatch(folders, folder -> apply(folder, fill, overlay, overlayScale));
    }

    public BatchResult resetAll(List<Path> folders) {
        return runBatch(folders, FolderService::reset);
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /** Reverts the most recent batch. */
    public BatchResult undoLast() {
        List<Path> ok = new ArrayList<>();
        Map<Path, String> failed = new LinkedHashMap<>();
        if (undoStack.isEmpty()) {
            return new BatchResult(ok, failed);
        }
        for (FolderSnapshot snapshot : undoStack.pop()) {
            try {
                snapshot.restore();
                ok.add(snapshot.folder());
            } catch (IOException e) {
                failed.put(snapshot.folder(), e.getMessage());
            }
        }
        return new BatchResult(ok, failed);
    }

    private BatchResult runBatch(List<Path> folders, IoAction action) {
        List<FolderSnapshot> snapshots = new ArrayList<>();
        List<Path> ok = new ArrayList<>();
        Map<Path, String> failed = new LinkedHashMap<>();

        for (Path folder : folders) {
            FolderSnapshot snapshot = null;
            try {
                snapshot = FolderSnapshot.capture(folder);
                action.run(folder);
                snapshots.add(snapshot);
                ok.add(folder);
            } catch (IOException | RuntimeException e) {
                if (snapshot != null) {
                    try {
                        snapshot.restore(); // roll back a half-done change
                    } catch (IOException ignored) {
                        // Nothing more we can do
                    }
                }
                failed.put(folder, e.getMessage());
            }
        }
        if (!snapshots.isEmpty()) {
            undoStack.push(snapshots);
        }
        return new BatchResult(ok, failed);
    }

    private static void apply(Path folder, FolderFill fill, BufferedImage overlay, double overlayScale)
            throws IOException {
        Path ico = folder.resolve(ICON_NAME);
        FileAttrs.unhide(ico);

        List<BufferedImage> images = new ArrayList<>();
        for (int size : SIZES) {
            images.add(FolderIconRenderer.render(size, fill, overlay, overlayScale));
        }
        IcoWriter.write(ico, images);
        FileAttrs.hide(ico);

        StyleStore.write(folder, fill, overlayScale, overlay);
        FolderCustomizer.applyIcon(folder, ico);
    }

    private static void reset(Path folder) throws IOException {
        FolderCustomizer.reset(folder);
        Path ico = folder.resolve(ICON_NAME);
        FileAttrs.unhide(ico);
        Files.deleteIfExists(ico);
        StyleStore.delete(folder);
    }
}
