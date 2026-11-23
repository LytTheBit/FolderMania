package com.foldermania.shell;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Applies and removes a folder icon through desktop.ini, touching nothing but the IconResource key. */
public final class FolderCustomizer {

    private static final int SHCNE_UPDATEITEM = 0x00002000;
    private static final int SHCNF_PATHW = 0x0005;

    private static final String INI_NAME = "desktop.ini";
    private static final String SECTION = ".ShellClassInfo";
    private static final String ICON_KEY = "IconResource";

    private interface Shell32 extends Library {
        Shell32 INSTANCE = Native.load("shell32", Shell32.class);

        void SHChangeNotify(int event, int flags, WString item1, Pointer item2);
    }

    private FolderCustomizer() {}

    /**
     * Applies a custom icon to a folder. An existing desktop.ini is updated in place: only
     * IconResource is written, so keys such as LocalizedResourceName or InfoTip survive.
     */
    public static void applyIcon(Path folder, Path iconFile) throws IOException {
        Path ini = folder.resolve(INI_NAME);

        DesktopIni content;
        if (Files.exists(ini)) {
            makeWritable(ini);
            content = DesktopIni.read(ini);
        } else {
            content = DesktopIni.empty();
        }

        // Use a relative reference when the icon lives inside the folder,
        // so the customization survives moving or renaming the folder
        Path absFolder = folder.toAbsolutePath().normalize();
        Path absIcon = iconFile.toAbsolutePath().normalize();
        String iconRef = absFolder.equals(absIcon.getParent())
                ? absIcon.getFileName().toString()
                : absIcon.toString();

        content.set(SECTION, ICON_KEY, iconRef + ",0");
        content.write(ini);

        Files.setAttribute(ini, "dos:hidden", true);
        Files.setAttribute(ini, "dos:system", true);
        // Explorer only reads desktop.ini if the folder is read-only or system
        Files.setAttribute(folder, "dos:readonly", true);

        refresh(folder);
    }

    /**
     * Removes the custom icon and restores the default one. desktop.ini is deleted only if
     * nothing else is left in it; otherwise the other keys are kept and the folder stays read-only,
     * because Explorer needs that flag to keep reading them.
     */
    public static void reset(Path folder) throws IOException {
        Path ini = folder.resolve(INI_NAME);
        boolean iniRemains = false;

        if (Files.exists(ini)) {
            makeWritable(ini);
            DesktopIni content = DesktopIni.read(ini);
            boolean changed = content.remove(SECTION, ICON_KEY);

            if (content.hasEntries()) {
                if (changed) {
                    content.write(ini);
                }
                Files.setAttribute(ini, "dos:hidden", true);
                Files.setAttribute(ini, "dos:system", true);
                iniRemains = true;
            } else {
                Files.delete(ini);
            }
        }

        if (!iniRemains) {
            Files.setAttribute(folder, "dos:readonly", false);
        }
        refresh(folder);
    }

    /** Tells Explorer to redraw the folder. */
    public static void refresh(Path folder) {
        Shell32.INSTANCE.SHChangeNotify(
                SHCNE_UPDATEITEM, SHCNF_PATHW,
                new WString(folder.toAbsolutePath().toString()), null);
    }

    /** desktop.ini is normally hidden+system (and sometimes read-only): clear that before editing it. */
    private static void makeWritable(Path ini) throws IOException {
        Files.setAttribute(ini, "dos:hidden", false);
        Files.setAttribute(ini, "dos:system", false);
        Files.setAttribute(ini, "dos:readonly", false);
    }
}
