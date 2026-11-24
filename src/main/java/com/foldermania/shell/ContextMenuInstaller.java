package com.foldermania.shell;

import com.foldermania.i18n.I18n;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

import java.nio.file.Path;

/**
 * Adds or removes the "Customize with Folder Mania" entry in the folder context menu
 * (per-user, no admin needed). The entry text follows the app language at install time.
 */
public final class ContextMenuInstaller {

    private static final WinReg.HKEY ROOT = WinReg.HKEY_CURRENT_USER;
    private static final String KEY = "Software\\Classes\\Directory\\shell\\FolderMania";
    private static final String COMMAND_KEY = KEY + "\\command";

    private ContextMenuInstaller() {}

    public static boolean isInstalled() {
        return Advapi32Util.registryKeyExists(ROOT, KEY);
    }

    public static void install() {
        String exe = ProcessHandle.current().info().command()
                .orElseThrow(() -> new IllegalStateException("Cannot detect the executable path"));
        String name = Path.of(exe).getFileName().toString().toLowerCase();
        if (name.equals("java.exe") || name.equals("javaw.exe")) {
            throw new IllegalStateException("The context menu can only be installed from the packaged app");
        }

        Advapi32Util.registryCreateKey(ROOT, KEY);
        Advapi32Util.registrySetStringValue(ROOT, KEY, "", I18n.tr("context.entry"));
        Advapi32Util.registrySetStringValue(ROOT, KEY, "Icon", "\"" + exe + "\",0");
        Advapi32Util.registryCreateKey(ROOT, COMMAND_KEY);
        Advapi32Util.registrySetStringValue(ROOT, COMMAND_KEY, "", "\"" + exe + "\" \"%1\"");
    }

    public static void uninstall() {
        // The subkey must be deleted before its parent
        if (Advapi32Util.registryKeyExists(ROOT, COMMAND_KEY)) {
            Advapi32Util.registryDeleteKey(ROOT, COMMAND_KEY);
        }
        if (Advapi32Util.registryKeyExists(ROOT, KEY)) {
            Advapi32Util.registryDeleteKey(ROOT, KEY);
        }
    }
}
