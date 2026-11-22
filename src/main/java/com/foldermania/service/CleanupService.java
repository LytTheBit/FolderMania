package com.foldermania.service;

import com.foldermania.settings.AppSettings;
import com.foldermania.shell.ContextMenuInstaller;

import java.io.IOException;

/** Removes everything the app installed outside its own folder. Folder icons already applied are kept. */
public final class CleanupService {

    private CleanupService() {}

    public static void run() throws IOException {
        ContextMenuInstaller.uninstall();
        new AppSettings().deleteAll();
    }
}
