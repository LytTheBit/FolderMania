package com.foldermania.shell;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FolderCustomizer {

    private static final int SHCNE_UPDATEITEM = 0x00002000;
    private static final int SHCNF_PATHW = 0x0005;

    private interface Shell32 extends Library {
        Shell32 INSTANCE = Native.load("shell32", Shell32.class);

        void SHChangeNotify(int event, int flags, WString item1, Pointer item2);
    }

    private FolderCustomizer() {}

    /** Applies a custom icon to a folder via desktop.ini. */
    public static void applyIcon(Path folder, Path iconFile) throws IOException {
        Path ini = folder.resolve("desktop.ini");

        // An existing desktop.ini is hidden+system, so clear attributes before overwriting
        if (Files.exists(ini)) {
            Files.setAttribute(ini, "dos:hidden", false);
            Files.setAttribute(ini, "dos:system", false);
        }

        // Use a relative reference when the icon lives inside the folder,
        // so the customization survives moving or renaming the folder
        Path absFolder = folder.toAbsolutePath().normalize();
        Path absIcon = iconFile.toAbsolutePath().normalize();
        String iconRef = absFolder.equals(absIcon.getParent())
                ? absIcon.getFileName().toString()
                : absIcon.toString();

        String content = "[.ShellClassInfo]\r\n"
                + "IconResource=" + iconRef + ",0\r\n";

        // UTF-16 LE with BOM so non-ASCII paths work correctly
        byte[] bom = {(byte) 0xFF, (byte) 0xFE};
        byte[] body = content.getBytes(StandardCharsets.UTF_16LE);
        byte[] data = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, data, 0, bom.length);
        System.arraycopy(body, 0, data, bom.length, body.length);
        Files.write(ini, data);

        Files.setAttribute(ini, "dos:hidden", true);
        Files.setAttribute(ini, "dos:system", true);
        // Explorer only reads desktop.ini if the folder is read-only or system
        Files.setAttribute(folder, "dos:readonly", true);

        refresh(folder);
    }

    /** Removes the customization and restores the default icon. */
    public static void reset(Path folder) throws IOException {
        Path ini = folder.resolve("desktop.ini");
        if (Files.exists(ini)) {
            Files.setAttribute(ini, "dos:hidden", false);
            Files.setAttribute(ini, "dos:system", false);
            Files.delete(ini);
        }
        Files.setAttribute(folder, "dos:readonly", false);
        refresh(folder);
    }

    /** Tells Explorer to redraw the folder. */
    public static void refresh(Path folder) {
        Shell32.INSTANCE.SHChangeNotify(
                SHCNE_UPDATEITEM, SHCNF_PATHW,
                new WString(folder.toAbsolutePath().toString()), null);
    }
}
