package com.foldermania;

import com.foldermania.ui.MainApp;

/**
 * Entry point of the packaged app. The main class must not extend javafx.application.Application,
 * otherwise JavaFX refuses to start when it is loaded from the classpath instead of the module path.
 */
public final class Launcher {

    private Launcher() {}

    public static void main(String[] args) {
        MainApp.main(args);
    }
}
