package com.foldermania.ui;

import com.foldermania.AppInfo;
import com.foldermania.icon.FolderIconRenderer;
import com.foldermania.icon.IconLibrary;
import com.foldermania.icon.OverlayImageLoader;
import com.foldermania.service.CleanupService;
import com.foldermania.service.FolderService;
import com.foldermania.service.StyleStore;
import com.foldermania.settings.AppSettings;
import com.foldermania.shell.ContextMenuInstaller;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

public class MainApp extends Application {

    private final AppSettings settings = new AppSettings();
    private final FolderService service = new FolderService();

    private final ObservableList<Path> folders = FXCollections.observableArrayList();
    private final ListView<Path> folderList = new ListView<>(folders);
    private final CheckBox recursive = new CheckBox("Include subfolders");
    private final ImageView preview = new ImageView();
    private final ColorPicker colorPicker = new ColorPicker(Color.web(Palette.DEFAULT_HEX));
    private final Slider scaleSlider = new Slider(0.3, 1.0, 0.75);
    private final FlowPane recentBox = new FlowPane(6, 6);
    private final Button applyButton = new Button("Apply");
    private final Button resetButton = new Button("Reset");
    private final Button undoButton = new Button("Undo");
    private final Label status = new Label();
    /** Every color swatch on screen (palette and recent colors), to highlight the selected one. */
    private final List<Button> swatches = new ArrayList<>();

    private Stage stage;
    private VBox content;
    private BufferedImage overlay;
    /** The library icon behind the current overlay, if it came from the library (so it can be recolored). */
    private IconLibrary.Entry libraryIcon;
    private IconLibrary.Style libraryStyle = IconLibrary.Style.OUTLINE;
    private final ColorPicker iconColorPicker = new ColorPicker(Color.web("#2B2B2B"));

    @Override
    public void start(Stage stage) {
        List<String> args = getParameters().getRaw();

        // Headless mode, to be run before uninstalling: folder-mania.exe --cleanup
        if (args.contains("--cleanup")) {
            try {
                CleanupService.run();
            } catch (Exception ex) {
                System.err.println("Cleanup failed: " + ex.getMessage());
            }
            Platform.exit();
            return;
        }

        this.stage = stage;
        for (int size : new int[]{16, 32, 48, 64, 128, 256}) {
            stage.getIcons().add(icon(size));
        }

        // --- Folders ---
        folderList.setPrefHeight(110);
        folderList.setPlaceholder(new Label("No folders added\nDrop folders here or use \"Add folder...\""));
        folderList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        folderList.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                loadStyle(sel);
            }
        });

        Button addFolder = new Button("Add folder...");
        addFolder.setOnAction(e -> chooseFolder());
        Button removeFolder = new Button("Remove");
        removeFolder.setOnAction(e ->
                folders.removeAll(new ArrayList<>(folderList.getSelectionModel().getSelectedItems())));
        Button clearFolders = new Button("Clear");
        clearFolders.setOnAction(e -> folders.clear());

        // --- Color ---
        colorPicker.valueProperty().addListener((obs, old, val) -> {
            updatePreview();
            updateSwatchSelection();
        });
        GridPane paletteGrid = buildPaletteGrid();
        refreshRecent();

        // --- Overlay icon ---
        IconGallery gallery = new IconGallery(this::pickLibraryIcon);
        iconColorPicker.setDisable(true);
        iconColorPicker.valueProperty().addListener((obs, old, val) -> renderLibraryOverlay());
        Button chooseOverlay = new Button("Load from file...");
        chooseOverlay.setOnAction(e -> chooseOverlay());
        Button clearOverlay = new Button("Clear icon");
        clearOverlay.setOnAction(e -> {
            overlay = null;
            clearLibraryIcon();
            updatePreview();
        });
        scaleSlider.valueProperty().addListener((obs, old, val) -> updatePreview());

        // --- Actions ---
        applyButton.setDefaultButton(true);
        applyButton.setOnAction(e -> apply());
        resetButton.setOnAction(e -> reset());
        undoButton.setDisable(true);
        undoButton.setOnAction(e -> runBatch("Undone", service::undoLast));

        status.setWrapText(true);

        content = new VBox(10,
                new Label("Folders"),
                folderList,
                new HBox(8, addFolder, removeFolder, clearFolders),
                recursive,
                preview,
                new Label("Color"),
                colorPicker,
                paletteGrid,
                new Label("Recent colors"),
                recentBox,
                new Label("Icon"),
                gallery,
                new HBox(8, new Label("Icon color"), iconColorPicker),
                new HBox(8, chooseOverlay, clearOverlay),
                new HBox(8, new Label("Icon size"), scaleSlider),
                new HBox(8, applyButton, resetButton, undoButton),
                status);
        content.setPadding(new Insets(16));
        content.setAlignment(Pos.TOP_CENTER);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);

        BorderPane root = new BorderPane(scroll);
        root.setTop(buildMenuBar());
        setupDragAndDrop(root);

        // Folders passed on the command line (e.g. from the context menu)
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                addFolder(Path.of(arg));
            }
        }

        updatePreview();
        stage.setTitle(AppInfo.NAME);
        stage.setScene(new Scene(root, 420, 720));
        stage.show();
    }

    // ---------- Folder list ----------

    private void chooseFolder() {
        File dir = new DirectoryChooser().showDialog(stage);
        if (dir != null) {
            addFolder(dir.toPath());
        }
    }

    private void addFolder(Path path) {
        addFolders(List.of(path));
    }

    /**
     * Adds the folders that are not already in the list (anything that is not a folder is skipped)
     * and selects the new ones. Selecting also loads the current style of the last one, if any.
     *
     * @return how many folders were actually added
     */
    private int addFolders(List<Path> paths) {
        int firstNew = folders.size();
        for (Path path : paths) {
            Path normalized = path.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized) && !folders.contains(normalized)) {
                folders.add(normalized);
            }
        }
        int added = folders.size() - firstNew;
        if (added > 0) {
            folderList.getSelectionModel().clearSelection();
            folderList.getSelectionModel().selectRange(firstNew, folders.size());
        }
        return added;
    }

    // ---------- Drag and drop ----------

    private static final String DROP_HIGHLIGHT =
            "-fx-border-color: #3B82F6; -fx-border-width: 2; -fx-border-style: dashed;"
                    + " -fx-control-inner-background: #EAF2FF;";

    /** Lets the user drop folders from the desktop or Explorer anywhere on the window. */
    private void setupDragAndDrop(Node target) {
        target.setOnDragOver(e -> {
            if (containsFolder(e.getDragboard())) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        target.setOnDragEntered(e -> {
            if (containsFolder(e.getDragboard())) {
                folderList.setStyle(DROP_HIGHLIGHT);
            }
        });
        target.setOnDragExited(e -> folderList.setStyle(""));
        target.setOnDragDropped(e -> {
            Dragboard board = e.getDragboard();
            boolean success = false;
            if (board.hasFiles()) {
                List<File> dropped = board.getFiles();
                List<Path> paths = new ArrayList<>();
                for (File file : dropped) {
                    paths.add(file.toPath());
                }
                int added = addFolders(paths);
                long notFolders = dropped.stream().filter(f -> !f.isDirectory()).count();

                String text = added == 0
                        ? "Nothing added: already in the list"
                        : "Added " + added + (added == 1 ? " folder" : " folders");
                if (notFolders > 0) {
                    text += " (" + notFolders + (notFolders == 1 ? " file" : " files") + " ignored)";
                }
                status.setText(text);
                success = added > 0;
            }
            e.setDropCompleted(success);
            e.consume();
        });
    }

    private static boolean containsFolder(Dragboard board) {
        return board.hasFiles() && board.getFiles().stream().anyMatch(File::isDirectory);
    }

    /** Shows the style currently applied to a folder in the editor. */
    private void loadStyle(Path folder) {
        Optional<StyleStore.Style> style = StyleStore.read(folder);
        if (style.isEmpty()) {
            return;
        }
        StyleStore.Style s = style.get();
        colorPicker.setValue(Color.web(s.colorHex()));
        scaleSlider.setValue(s.overlayScale());
        clearLibraryIcon();
        overlay = s.overlay();
        updatePreview();
        status.setText("Loaded current style of " + folder);
    }

    // ---------- Actions ----------

    private void apply() {
        if (folders.isEmpty()) {
            status.setText("Add at least one folder first.");
            return;
        }
        java.awt.Color color = toAwt(colorPicker.getValue());
        BufferedImage image = overlay;
        double scale = scaleSlider.getValue();
        boolean includeSub = recursive.isSelected();
        List<Path> roots = List.copyOf(folders);

        settings.addRecentColor(toHex(colorPicker.getValue()));
        refreshRecent();

        runBatch("Applied", () ->
                service.applyAll(FolderService.expand(roots, includeSub), color, image, scale));
    }

    private void reset() {
        if (folders.isEmpty()) {
            status.setText("Add at least one folder first.");
            return;
        }
        boolean includeSub = recursive.isSelected();
        List<Path> roots = List.copyOf(folders);
        runBatch("Reset", () -> service.resetAll(FolderService.expand(roots, includeSub)));
    }

    /** Runs a batch off the UI thread so large folder trees don't freeze the window. */
    private void runBatch(String label, Callable<FolderService.BatchResult> job) {
        content.setDisable(true);
        Task<FolderService.BatchResult> task = new Task<>() {
            @Override
            protected FolderService.BatchResult call() throws Exception {
                return job.call();
            }
        };
        task.setOnSucceeded(e -> {
            content.setDisable(false);
            report(label, task.getValue());
        });
        task.setOnFailed(e -> {
            content.setDisable(false);
            status.setText("Error: " + task.getException().getMessage());
        });
        Thread thread = new Thread(task, "folder-batch");
        thread.setDaemon(true);
        thread.start();
    }

    private void report(String label, FolderService.BatchResult result) {
        String text = label + ": " + result.succeeded().size() + " folder(s)";
        if (!result.failed().isEmpty()) {
            Map.Entry<Path, String> first = result.failed().entrySet().iterator().next();
            text += ", " + result.failed().size() + " failed (e.g. "
                    + first.getKey() + ": " + first.getValue() + ")";
        }
        status.setText(text);
        undoButton.setDisable(!service.canUndo());
    }

    private void chooseOverlay() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images (SVG, PNG, JPG, GIF)",
                        "*.svg", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                new FileChooser.ExtensionFilter("SVG", "*.svg"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            overlay = OverlayImageLoader.load(file.toPath());
            clearLibraryIcon();
            updatePreview();
        } catch (Exception ex) {
            status.setText("Cannot read image: " + ex.getMessage());
        }
    }

    // ---------- Icon library ----------

    /** Called by the gallery when the user clicks an icon. */
    private void pickLibraryIcon(IconLibrary.Entry entry, IconLibrary.Style style) {
        libraryIcon = entry;
        libraryStyle = style;
        iconColorPicker.setDisable(false);
        renderLibraryOverlay();
    }

    /** Rasterizes the chosen library icon in the current icon color and makes it the overlay. */
    private void renderLibraryOverlay() {
        if (libraryIcon == null) {
            return;
        }
        try {
            overlay = IconLibrary.get().render(
                    libraryIcon, libraryStyle, toHex(iconColorPicker.getValue()), 512);
            updatePreview();
        } catch (Exception ex) {
            status.setText("Cannot load icon: " + ex.getMessage());
        }
    }

    /** The overlay no longer comes from the library: the icon color does not apply to it. */
    private void clearLibraryIcon() {
        libraryIcon = null;
        iconColorPicker.setDisable(true);
    }

    // ---------- Menu ----------

    private MenuBar buildMenuBar() {
        MenuItem install = new MenuItem("Add to folder context menu");
        install.setOnAction(e -> toggleContextMenu(true));
        MenuItem uninstall = new MenuItem("Remove from folder context menu");
        uninstall.setOnAction(e -> toggleContextMenu(false));
        MenuItem cleanup = new MenuItem("Clean up before uninstalling...");
        cleanup.setOnAction(e -> confirmCleanup());
        Menu tools = new Menu("Tools", null, install, uninstall, new SeparatorMenuItem(), cleanup);

        MenuItem about = new MenuItem("About " + AppInfo.NAME);
        about.setOnAction(e -> showAbout());
        Menu help = new Menu("Help", null, about);

        return new MenuBar(tools, help);
    }

    private void toggleContextMenu(boolean install) {
        try {
            if (install) {
                ContextMenuInstaller.install();
                status.setText("Context menu entry added.");
            } else {
                ContextMenuInstaller.uninstall();
                status.setText("Context menu entry removed.");
            }
        } catch (Exception ex) {
            status.setText("Error: " + ex.getMessage());
        }
    }

    private void confirmCleanup() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "This removes the context menu entry and the saved settings. "
                        + "Folders you already customized keep their icons. Continue?",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("Clean up before uninstalling");
        confirm.initOwner(stage);
        confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            try {
                CleanupService.run();
                Alert done = new Alert(Alert.AlertType.INFORMATION,
                        "Done. You can now uninstall " + AppInfo.NAME + ".");
                done.initOwner(stage);
                done.showAndWait();
                Platform.exit();
            } catch (Exception ex) {
                status.setText("Error: " + ex.getMessage());
            }
        });
    }

    private void showAbout() {
        Alert about = new Alert(Alert.AlertType.INFORMATION);
        about.setTitle("About " + AppInfo.NAME);
        about.setHeaderText(AppInfo.NAME + " " + AppInfo.VERSION);
        String text = "Customize your Windows folders with colors and icons."
                + "\n\nIcons: Tabler Icons (MIT License), https://tabler.io/icons";
        if (!AppInfo.REPO_URL.isBlank()) {
            text += "\n\n" + AppInfo.REPO_URL;
        }
        about.setContentText(text);
        about.setGraphic(new ImageView(icon(64)));
        about.initOwner(stage);
        about.showAndWait();
    }

    // ---------- Colors and preview ----------

    /** 11 color families as columns, 3 shades as rows (dark on top, light at the bottom). */
    private GridPane buildPaletteGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(4);
        grid.setVgap(4);
        grid.setAlignment(Pos.CENTER);
        for (int col = 0; col < Palette.FAMILIES.size(); col++) {
            Palette.Family family = Palette.FAMILIES.get(col);
            for (int row = 0; row < Palette.Shade.values().length; row++) {
                Palette.Shade shade = Palette.Shade.values()[row];
                String name = family.name() + " (" + shade.name().toLowerCase() + ")";
                grid.add(swatch(family.hex(shade), name), col, row);
            }
        }
        return grid;
    }

    private Button swatch(String hex, String tooltip) {
        Button button = new Button();
        button.setMinSize(26, 26);
        button.setPrefSize(26, 26);
        button.setMaxSize(26, 26);
        button.getProperties().put("hex", hex);
        button.setStyle(swatchStyle(hex, false));
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(e -> colorPicker.setValue(Color.web(hex)));
        swatches.add(button);
        return button;
    }

    private static String swatchStyle(String hex, boolean selected) {
        return "-fx-background-color: " + hex + "; -fx-background-radius: 6;"
                + (selected
                ? " -fx-border-color: #FFFFFF; -fx-border-width: 2; -fx-border-radius: 6;"
                + " -fx-effect: dropshadow(gaussian, #000000A0, 4, 0.5, 0, 0);"
                : " -fx-border-color: #00000030; -fx-border-radius: 6;");
    }

    /** Draws a white ring around the swatch(es) that match the current color. */
    private void updateSwatchSelection() {
        String current = toHex(colorPicker.getValue());
        for (Button button : swatches) {
            String hex = (String) button.getProperties().get("hex");
            button.setStyle(swatchStyle(hex, hex.equalsIgnoreCase(current)));
        }
    }

    private void refreshRecent() {
        // Drop the old recent swatches from the tracking list before rebuilding them
        for (var node : recentBox.getChildren()) {
            if (node instanceof Button b) {
                swatches.remove(b);
            }
        }
        recentBox.getChildren().clear();
        for (String hex : settings.recentColors()) {
            recentBox.getChildren().add(swatch(hex, hex));
        }
        if (recentBox.getChildren().isEmpty()) {
            recentBox.getChildren().add(new Label("No recent colors yet"));
        }
        updateSwatchSelection();
    }

    private void updatePreview() {
        BufferedImage img = FolderIconRenderer.render(
                128, toAwt(colorPicker.getValue()), overlay, scaleSlider.getValue());
        preview.setImage(SwingFXUtils.toFXImage(img, null));
    }

    private static Image icon(int size) {
        return SwingFXUtils.toFXImage(
                FolderIconRenderer.render(size, FolderIconRenderer.DEFAULT_COLOR, null, 0.6), null);
    }

    private static java.awt.Color toAwt(Color c) {
        return new java.awt.Color(
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
