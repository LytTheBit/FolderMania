# Folder Mania

Customize your Windows folders with **colors** and **overlay icons**, in bulk, without touching the files inside them.

Folder Mania is a desktop app written in Java 21 with JavaFX. It generates a custom folder icon (the classic folder shape in the color you pick, with an optional icon on top) and applies it through the standard Windows `desktop.ini` mechanism, so the result shows up in Explorer without any background process or shell extension.

<!-- Add a screenshot here, e.g. ![Folder Mania](docs/screenshot.png) -->

## Features

- **Color palette**: 11 color families with 3 shades each, a free color picker, and the last 8 colors you used.
- **Built-in icon library**: over 5,000 icons from [Tabler Icons](https://tabler.io/icons), in outline and filled styles, with search, categories, a "Popular" tab, and a custom icon color.
- **Your own icons**: load a PNG, JPG, GIF or **SVG** file (SVGs are rasterized with [JSVG](https://github.com/weisj/jsvg)).
- **Live preview** and an adjustable icon size.
- **Batch editing**: add many folders at once (button, drag and drop, or command line) and optionally include all their subfolders. Hidden and system subfolders are skipped.
- **Undo**: revert the last batch. A failed folder is rolled back, so you never end up with a half-applied change.
- **Edit later**: the chosen style is stored in the folder itself, so selecting a customized folder loads its current color and icon back into the editor.
- **Explorer integration**: optional "Customize with Folder Mania" entry in the folder context menu (per-user, no admin rights).
- **Clean uninstall**: removes the context menu entry and the saved settings.

## Requirements

- Windows 10 or 11
- To run from source: JDK 21 (Gradle is configured with a Java 21 toolchain)
- To build the installer: [WiX Toolset 3.x](https://wixtoolset.org) on the `PATH`

The packaged app bundles its own Java runtime, so end users do not need to install Java.

## Run from source

```
git clone https://github.com/LytTheBit/FolderMania.git
cd FolderMania
gradlew run
```

You can also open the folder in IntelliJ IDEA (it imports the Gradle project automatically) and run the Gradle task `run`.

## Build a Windows app

Run these on Windows: the JavaFX jars that get bundled are specific to the current OS.

| Task | Output |
|------|--------|
| `gradlew packageApp` | `build/jpackage/output/Folder Mania/Folder Mania.exe` (portable folder, no install needed) |
| `gradlew packageInstaller` | `build/jpackage/output/Folder Mania-<version>.exe` (per-user installer with Start menu and desktop shortcut) |

Use `gradlew packageInstaller -PinstallerType=msi` to get an `.msi` instead of an `.exe`.

> **Tip:** if the project lives inside a OneDrive folder, the build directory is automatically moved to `%LOCALAPPDATA%\FolderMania-build` to avoid sync locks. The output paths above change accordingly.

## Usage

1. Add one or more folders (**Add folder...**, drag and drop onto the window, or the context menu).
2. Pick a color and, optionally, an icon from the library or from a file.
3. Adjust the icon size and check the preview.
4. Click **Apply**. Use **Reset** to restore the default folder icon, or **Undo** to revert the last operation.

### Context menu

Start the packaged app, then choose **Tools > Add to folder context menu**. Right-click any folder and pick **Customize with Folder Mania**. On Windows 11 the entry appears under "Show more options".

The entry can only be installed from the packaged app, not from the IDE, because it needs the path of the real `.exe`.

### Command line

```
"Folder Mania.exe" [folder ...]     Open the app with the given folders already added
"Folder Mania.exe" --cleanup        Remove the context menu entry and saved settings, then exit
```

### Uninstalling

Before uninstalling, use **Tools > Clean up before uninstalling...** (or run the app with `--cleanup`). Folders you already customized keep their icons.

## How it works

For each customized folder, Folder Mania writes three small hidden files next to your data:

| File | Purpose |
|------|---------|
| `.foldermania.ico` | The generated multi-size icon (16 to 256 px) |
| `.foldermania.properties` | The chosen color and icon size |
| `.foldermania-overlay.png` | The overlay image, so the style can be edited later |

It then sets the `IconResource` key in the folder's `desktop.ini` (updating an existing file in place, so other keys like `LocalizedResourceName` are preserved) and notifies Explorer to redraw the folder. The icon is referenced with a relative path, so the customization survives moving or renaming the folder.

Windows only reads `desktop.ini` from folders that are read-only or system folders, so the folder gets the read-only attribute. This flag is not related to whether you can write files in the folder.

Settings (recent colors) are stored in `%APPDATA%\FolderMania\settings.properties`.

## Project structure

```
src/main/java/com/foldermania/
├── ui/         JavaFX interface (main window, icon gallery, color palette)
├── icon/       Icon rendering, .ico writer, SVG loading, Tabler icon library
├── service/    Batch operations, undo, per-folder style storage, cleanup
├── shell/      Windows integration: desktop.ini, Explorer refresh, context menu (JNA)
├── settings/   User settings
└── tools/      Development tools (app icon generator)
```

## Tech stack

- Java 21, Gradle (Kotlin DSL)
- JavaFX 21 for the UI
- [JNA](https://github.com/java-native-access/jna) for the Windows registry and shell calls
- [JSVG](https://github.com/weisj/jsvg) for SVG rendering
- `jpackage` for the portable app and the installer

## Credits

The built-in icons are from [Tabler Icons](https://tabler.io/icons) by Paweł Kuna, released under the MIT License (see `src/main/resources/icons/tabler/LICENSE.txt`).