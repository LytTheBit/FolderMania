package com.foldermania.ui;

import com.foldermania.AppInfo;
import com.foldermania.icon.FolderIconRenderer;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
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
    private final ColorPicker colorPicker = new ColorPicker(Color.web(AppSettings.PRESETS.get(0)));
    private final Slider scaleSlider = new Slider(0.3, 1.0, 0.6);
    private final FlowPane recentBox = new FlowPane(6, 6);
    private final Button applyButton = new Button("Apply");
    private final Button resetButton = new Button("Reset");
    private final Button undoButton = new Button("Undo");
    private final Label status = new Label();

    private Stage stage;
    private VBox content;
    private BufferedImage overlay;

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
        folderList.setPlaceholder(new Label("No folders added"));
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
        colorPicker.valueProperty().addListener((obs, old, val) -> updatePreview());
        FlowPane presetBox = new FlowPane(6, 6);
        for (String hex : AppSettings.PRESETS) {
            presetBox.getChildren().add(swatch(hex));
        }
        refreshRecent();

        // --- Overlay icon ---
        Button chooseOverlay = new Button("Choose icon...");
        chooseOverlay.setOnAction(e -> chooseOverlay());
        Button clearOverlay = new Button("Clear icon");
        clearOverlay.setOnAction(e -> {
            overlay = null;
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
                presetBox,
                new Label("Recent colors"),
                recentBox,
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
        if (!Files.isDirectory(path) || folders.contains(path)) {
            return;
        }
        folders.add(path);
        // Selecting it also loads its current style, if any
        folderList.getSelectionModel().clearAndSelect(folders.size() - 1);
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
            updatePreview();
        } catch (Exception ex) {
            status.setText("Cannot read image: " + ex.getMessage());
        }
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
        String text = "Customize your Windows folders with colors and icons.";
        if (!AppInfo.REPO_URL.isBlank()) {
            text += "\n\n" + AppInfo.REPO_URL;
        }
        about.setContentText(text);
        about.setGraphic(new ImageView(icon(64)));
        about.initOwner(stage);
        about.showAndWait();
    }

    // ---------- Colors and preview ----------

    private Button swatch(String hex) {
        Button button = new Button();
        button.setMinSize(24, 24);
        button.setPrefSize(24, 24);
        button.setMaxSize(24, 24);
        button.setStyle("-fx-background-color: " + hex + "; -fx-background-radius: 12;"
                + " -fx-border-color: #00000030; -fx-border-radius: 12;");
        button.setTooltip(new Tooltip(hex));
        button.setOnAction(e -> colorPicker.setValue(Color.web(hex)));
        return button;
    }

    private void refreshRecent() {
        recentBox.getChildren().clear();
        for (String hex : settings.recentColors()) {
            recentBox.getChildren().add(swatch(hex));
        }
        if (recentBox.getChildren().isEmpty()) {
            recentBox.getChildren().add(new Label("No recent colors yet"));
        }
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
