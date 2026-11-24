package com.foldermania.ui;

import com.foldermania.AppInfo;
import com.foldermania.i18n.I18n;
import com.foldermania.icon.AppIcon;
import com.foldermania.icon.FolderFill;
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
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
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
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
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

    private static final String DEFAULT_ICON_COLOR = "#26272B";
    /** Second color proposed when the user switches to gradient or split for the first time. */
    private static final String DEFAULT_SECOND_COLOR = "#E5484D";
    private static final int PREVIEW_SIZE = 224;
    private static final int[] SMALL_PREVIEWS = {48, 32, 16};

    private final AppSettings settings = new AppSettings();
    private final FolderService service = new FolderService();

    // --- Folders ---
    private final ObservableList<Path> folders = FXCollections.observableArrayList();
    private final ListView<Path> folderList = new ListView<>(folders);
    private final CheckBox recursive = new CheckBox();

    // --- Preview ---
    private final ImageView preview = new ImageView();
    private final List<ImageView> smallPreviews = new ArrayList<>();

    // --- Folder style: mode and the two colors. The selector edits whichever color is "active". ---
    private final ObjectProperty<FolderFill.Mode> mode = new SimpleObjectProperty<>(FolderFill.Mode.SOLID);
    private final ObjectProperty<Color> color1 = new SimpleObjectProperty<>(Color.web(Palette.DEFAULT_HEX));
    private final ObjectProperty<Color> color2 = new SimpleObjectProperty<>(Color.web(DEFAULT_SECOND_COLOR));
    private final ColorSelector folderColors = new ColorSelector(Color.web(Palette.DEFAULT_HEX));
    private final ToggleGroup modeGroup = new ToggleGroup();
    private final ToggleGroup slotGroup = new ToggleGroup();
    private final List<ToggleButton> slotButtons = new ArrayList<>();
    private int activeSlot = 0;
    /** True while the selector is being set from code, so that it does not overwrite a color. */
    private boolean syncingSelector;

    // --- Overlay icon ---
    private final ColorSelector iconColors = new ColorSelector(Color.web(DEFAULT_ICON_COLOR));
    private final Slider scaleSlider = new Slider(0.3, 1.0, 0.75);
    private final BooleanProperty libraryIconActive = new SimpleBooleanProperty(false);
    private BufferedImage overlay;
    /** The library icon behind the current overlay, if it came from the library (so it can be recolored). */
    private IconLibrary.Entry libraryIcon;
    private IconLibrary.Style libraryStyle = IconLibrary.Style.OUTLINE;

    // --- Actions ---
    private final Button applyButton = new Button();
    private final Button resetButton = new Button();
    private final Button undoButton = new Button();
    private final Label status = new Label();

    private Stage stage;
    private Region busyArea;

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
        I18n.setLanguage(I18n.Language.fromCode(settings.language(), I18n.language()));
        for (int size : new int[]{16, 32, 48, 64, 128, 256}) {
            stage.getIcons().add(appIcon(size));
        }

        buildStyleBindings();
        folderColors.setRecent(settings.recentColors());
        iconColors.setRecent(settings.recentIconColors());

        // Left: what you see and what you change. Right: how it should look.
        HBox center = new HBox(16, buildLeftColumn(), buildRightColumn());
        center.setPadding(new Insets(16, 16, 8, 16));
        busyArea = center;

        BorderPane root = new BorderPane(center);
        root.setTop(buildMenuBar());
        root.setBottom(buildActionBar());
        setupDragAndDrop(root);

        // Folders passed on the command line (e.g. from the context menu)
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                addFolder(Path.of(arg));
            }
        }

        status.setText(I18n.tr("status.ready"));
        I18n.languageProperty().addListener((obs, old, now) -> status.setText(I18n.tr("status.ready")));
        updatePreview();

        Scene scene = new Scene(root, 1120, 800);
        scene.getStylesheets().add(stylesheet());
        stage.setTitle(AppInfo.NAME);
        stage.setMinWidth(1000);
        stage.setMinHeight(720);
        stage.setScene(scene);
        stage.show();
    }

    // ---------- Layout ----------

    /** Left column: the live preview, then the list of folders to change. */
    private VBox buildLeftColumn() {
        // Preview
        preview.setFitWidth(PREVIEW_SIZE);
        preview.setFitHeight(PREVIEW_SIZE);
        preview.setPreserveRatio(true);
        preview.setSmooth(true);
        StackPane stagePane = new StackPane(preview);
        stagePane.getStyleClass().add("preview-stage");

        HBox sizes = new HBox(16);
        sizes.setAlignment(Pos.BOTTOM_CENTER);
        for (int size : SMALL_PREVIEWS) {
            ImageView view = new ImageView();
            view.setFitWidth(size);
            view.setFitHeight(size);
            smallPreviews.add(view);
            sizes.getChildren().add(view);
        }

        VBox previewCard = card(
                title("preview.title"),
                stagePane,
                mutedLabel("preview.sizes"),
                sizes);
        previewCard.setAlignment(Pos.TOP_CENTER);

        // Folders
        folderList.setPrefHeight(120);
        folderList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        folderList.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                loadStyle(sel);
            }
        });
        Label placeholder = new Label();
        placeholder.textProperty().bind(I18n.binding("folders.placeholder"));
        placeholder.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        placeholder.getStyleClass().add("muted");
        folderList.setPlaceholder(placeholder);
        VBox.setVgrow(folderList, Priority.ALWAYS);

        Button addFolder = button("folders.add");
        addFolder.setOnAction(e -> chooseFolder());
        Button removeFolder = button("folders.remove");
        removeFolder.setOnAction(e ->
                folders.removeAll(new ArrayList<>(folderList.getSelectionModel().getSelectedItems())));
        Button clearFolders = button("folders.clear");
        clearFolders.setOnAction(e -> folders.clear());
        recursive.textProperty().bind(I18n.binding("folders.recursive"));

        VBox foldersCard = card(
                title("folders.title"),
                folderList,
                new HBox(8, addFolder, removeFolder, clearFolders),
                recursive);
        VBox.setVgrow(foldersCard, Priority.ALWAYS);

        VBox left = new VBox(16, previewCard, foldersCard);
        left.setPrefWidth(360);
        left.setMinWidth(340);
        left.setMaxWidth(380);
        return left;
    }

    /** Right column: a switch between the folder settings and the icon settings. */
    private VBox buildRightColumn() {
        ToggleButton folderTab = segment("tab.folder", "first");
        ToggleButton iconTab = segment("tab.icon", "last");
        ToggleGroup tabs = new ToggleGroup();
        folderTab.setToggleGroup(tabs);
        iconTab.setToggleGroup(tabs);
        folderTab.setSelected(true);
        keepOneSelected(tabs);

        ScrollPane folderPane = scrolling(buildFolderSettings());
        ScrollPane iconPane = scrolling(buildIconSettings());
        iconPane.visibleProperty().bind(iconTab.selectedProperty());
        iconPane.managedProperty().bind(iconTab.selectedProperty());
        folderPane.visibleProperty().bind(folderTab.selectedProperty());
        folderPane.managedProperty().bind(folderTab.selectedProperty());

        HBox tabBar = new HBox(folderTab, iconTab);
        tabBar.setAlignment(Pos.CENTER_LEFT);

        StackPane panes = new StackPane(folderPane, iconPane);
        VBox.setVgrow(panes, Priority.ALWAYS);

        VBox right = card(tabBar, panes);
        HBox.setHgrow(right, Priority.ALWAYS);
        return right;
    }

    /** Folder tab: solid / gradient / split, the color slots and the palette. */
    private VBox buildFolderSettings() {
        // Style switch
        ToggleButton solid = segment("style.solid", "first");
        ToggleButton gradient = segment("style.gradient", "middle");
        ToggleButton split = segment("style.split", "last");
        solid.setUserData(FolderFill.Mode.SOLID);
        gradient.setUserData(FolderFill.Mode.GRADIENT);
        split.setUserData(FolderFill.Mode.SPLIT);
        for (ToggleButton t : List.of(solid, gradient, split)) {
            t.setToggleGroup(modeGroup);
        }
        solid.setSelected(true);
        keepOneSelected(modeGroup);
        modeGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now != null) {
                mode.set((FolderFill.Mode) now.getUserData());
            }
        });

        // Color slots: which of the two colors the palette is editing
        ToggleButton slot1 = slotButton(0, color1);
        ToggleButton slot2 = slotButton(1, color2);
        keepOneSelected(slotGroup);
        slotGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now != null) {
                activeSlot = (Integer) now.getUserData();
                syncSelectorFromSlot();
            }
        });
        slot2.visibleProperty().bind(mode.isNotEqualTo(FolderFill.Mode.SOLID));
        slot2.managedProperty().bind(slot2.visibleProperty());
        Button swap = new Button("\u21C4");
        swap.getStyleClass().add("icon-action");
        swap.setTooltip(tooltip("folder.swap"));
        swap.visibleProperty().bind(slot2.visibleProperty());
        swap.managedProperty().bind(slot2.visibleProperty());
        swap.setOnAction(e -> {
            Color first = color1.get();
            color1.set(color2.get());
            color2.set(first);
            syncSelectorFromSlot();
        });
        HBox slots = new HBox(8, slot1, swap, slot2);
        slots.setAlignment(Pos.CENTER_LEFT);

        Label editing = new Label();
        editing.getStyleClass().add("muted");
        editing.textProperty().bind(Bindings.createStringBinding(
                () -> I18n.tr("folder.editing", I18n.tr(slotKeyFor(activeSlot, mode.get()))),
                mode, slotGroup.selectedToggleProperty(), I18n.languageProperty()));
        editing.visibleProperty().bind(slot2.visibleProperty());
        editing.managedProperty().bind(slot2.visibleProperty());

        // The palette edits the active slot
        folderColors.valueProperty().addListener((obs, old, now) -> {
            if (!syncingSelector) {
                (activeSlot == 0 ? color1 : color2).set(now);
            }
        });

        VBox box = new VBox(14,
                sectionTitle("folder.style"),
                new HBox(solid, gradient, split),
                slots,
                editing,
                folderColors);
        box.setPadding(new Insets(14, 4, 4, 2));
        return box;
    }

    /** Icon tab: icon color, the library, files, and the size. */
    private VBox buildIconSettings() {
        iconColors.disableProperty().bind(libraryIconActive.not());
        iconColors.valueProperty().addListener((obs, old, now) -> renderLibraryOverlay());

        Label hint = new Label();
        hint.textProperty().bind(I18n.binding("icon.colorHint"));
        hint.setWrapText(true);
        hint.getStyleClass().add("muted");
        hint.visibleProperty().bind(libraryIconActive.not());
        hint.managedProperty().bind(libraryIconActive.not());

        IconGallery gallery = new IconGallery(this::pickLibraryIcon);

        Button chooseOverlay = button("icon.load");
        chooseOverlay.setOnAction(e -> chooseOverlay());
        Button clearOverlay = button("icon.clear");
        clearOverlay.setOnAction(e -> {
            overlay = null;
            clearLibraryIcon();
            updatePreview();
        });
        scaleSlider.setPrefWidth(220);
        scaleSlider.valueProperty().addListener((obs, old, val) -> updatePreview());
        HBox sizeRow = new HBox(10, label("icon.size"), scaleSlider);
        sizeRow.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(14,
                sectionTitle("icon.library"),
                gallery,
                new HBox(8, chooseOverlay, clearOverlay),
                sizeRow,
                sectionTitle("icon.color"),
                hint,
                iconColors);
        box.setPadding(new Insets(14, 4, 4, 2));
        return box;
    }

    /** Bottom bar: status message on the left, the three actions on the right. */
    private HBox buildActionBar() {
        applyButton.textProperty().bind(I18n.binding("action.apply"));
        applyButton.getStyleClass().add("primary");
        applyButton.setDefaultButton(true);
        applyButton.setOnAction(e -> apply());

        resetButton.textProperty().bind(I18n.binding("action.reset"));
        resetButton.setOnAction(e -> reset());

        undoButton.textProperty().bind(I18n.binding("action.undo"));
        undoButton.setDisable(true);
        undoButton.setOnAction(e -> runBatch("status.undone", service::undoLast));

        status.setWrapText(true);
        status.getStyleClass().add("muted");
        HBox.setHgrow(status, Priority.ALWAYS);
        status.setMaxWidth(Double.MAX_VALUE);

        HBox bar = new HBox(10, status, undoButton, resetButton, applyButton);
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.getStyleClass().add("action-bar");
        return bar;
    }

    // ---------- Style state ----------

    /** Keeps the preview, the slot selection and the selector in step with the style state. */
    private void buildStyleBindings() {
        color1.addListener((obs, old, now) -> updatePreview());
        color2.addListener((obs, old, now) -> updatePreview());
        mode.addListener((obs, old, now) -> {
            if (now == FolderFill.Mode.SOLID && activeSlot != 0) {
                selectSlot(0); // only one color left to edit
            }
            updatePreview();
        });
    }

    private FolderFill currentFill() {
        java.awt.Color first = ColorSelector.toAwt(color1.get());
        java.awt.Color second = ColorSelector.toAwt(color2.get());
        return switch (mode.get()) {
            case SOLID -> FolderFill.solid(first);
            case GRADIENT -> FolderFill.gradient(first, second);
            case SPLIT -> FolderFill.split(first, second);
        };
    }

    /** Builds one of the two chips that choose which color the palette edits. */
    private ToggleButton slotButton(int slot, ObjectProperty<Color> color) {
        Rectangle swatch = new Rectangle(24, 24);
        swatch.setArcWidth(8);
        swatch.setArcHeight(8);
        swatch.setStroke(Color.web("#00000030"));
        swatch.fillProperty().bind(color);

        ToggleButton button = new ToggleButton();
        button.getStyleClass().add("chip");
        button.setGraphic(swatch);
        button.textProperty().bind(Bindings.createStringBinding(
                () -> I18n.tr(slotKeyFor(slot, mode.get())), mode, I18n.languageProperty()));
        button.setToggleGroup(slotGroup);
        button.setUserData(slot);
        slotButtons.add(button);
        if (slot == 0) {
            button.setSelected(true);
        }
        return button;
    }

    private static String slotKeyFor(int slot, FolderFill.Mode mode) {
        return switch (mode) {
            case SOLID -> "slot.solid";
            case GRADIENT -> slot == 0 ? "slot.start" : "slot.end";
            case SPLIT -> slot == 0 ? "slot.left" : "slot.right";
        };
    }

    private void selectSlot(int slot) {
        slotButtons.get(slot).setSelected(true);
    }

    /** Shows the color of the active slot in the palette selector. */
    private void syncSelectorFromSlot() {
        syncingSelector = true;
        try {
            folderColors.setValue((activeSlot == 0 ? color1 : color2).get());
        } finally {
            syncingSelector = false;
        }
    }

    // ---------- Folder list ----------

    private void chooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(I18n.tr("folders.choose"));
        File dir = chooser.showDialog(stage);
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

    /** Lets the user drop folders from the desktop or Explorer anywhere on the window. */
    private void setupDragAndDrop(Node target) {
        target.setOnDragOver(e -> {
            if (containsFolder(e.getDragboard())) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        target.setOnDragEntered(e -> {
            if (containsFolder(e.getDragboard()) && !folderList.getStyleClass().contains("drop-target")) {
                folderList.getStyleClass().add("drop-target");
            }
        });
        target.setOnDragExited(e -> folderList.getStyleClass().remove("drop-target"));
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
                        ? I18n.tr("status.dropNone")
                        : I18n.trn("status.dropAdded", added);
                if (notFolders > 0) {
                    text += " " + I18n.trn("status.dropIgnored", notFolders);
                }
                status.setText(text);
                success = added > 0;
            }
            folderList.getStyleClass().remove("drop-target");
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
        color1.set(ColorSelector.fromAwt(s.fill().primary()));
        color2.set(ColorSelector.fromAwt(s.fill().secondary()));
        for (var toggle : modeGroup.getToggles()) {
            if (toggle.getUserData() == s.fill().mode()) {
                toggle.setSelected(true);
            }
        }
        syncSelectorFromSlot();
        scaleSlider.setValue(s.overlayScale());
        clearLibraryIcon();
        overlay = s.overlay();
        updatePreview();
        status.setText(I18n.tr("status.loaded", folder));
    }

    // ---------- Actions ----------

    private void apply() {
        if (folders.isEmpty()) {
            status.setText(I18n.tr("status.needFolder"));
            return;
        }
        FolderFill fill = currentFill();
        BufferedImage image = overlay;
        double scale = scaleSlider.getValue();
        boolean includeSub = recursive.isSelected();
        List<Path> roots = List.copyOf(folders);

        settings.addRecentColor(ColorSelector.toHex(color1.get()));
        if (fill.isTwoColor()) {
            settings.addRecentColor(ColorSelector.toHex(color2.get()));
        }
        folderColors.setRecent(settings.recentColors());
        if (libraryIcon != null && image != null) {
            settings.addRecentIconColor(ColorSelector.toHex(iconColors.getValue()));
            iconColors.setRecent(settings.recentIconColors());
        }

        runBatch("status.applied", () ->
                service.applyAll(FolderService.expand(roots, includeSub), fill, image, scale));
    }

    private void reset() {
        if (folders.isEmpty()) {
            status.setText(I18n.tr("status.needFolder"));
            return;
        }
        boolean includeSub = recursive.isSelected();
        List<Path> roots = List.copyOf(folders);
        runBatch("status.reset", () -> service.resetAll(FolderService.expand(roots, includeSub)));
    }

    /** Runs a batch off the UI thread so large folder trees don't freeze the window. */
    private void runBatch(String messageKey, Callable<FolderService.BatchResult> job) {
        setBusy(true);
        Task<FolderService.BatchResult> task = new Task<>() {
            @Override
            protected FolderService.BatchResult call() throws Exception {
                return job.call();
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            report(messageKey, task.getValue());
        });
        task.setOnFailed(e -> {
            setBusy(false);
            status.setText(I18n.tr("status.error", task.getException().getMessage()));
        });
        Thread thread = new Thread(task, "folder-batch");
        thread.setDaemon(true);
        thread.start();
    }

    private void setBusy(boolean busy) {
        busyArea.setDisable(busy);
        applyButton.setDisable(busy);
        resetButton.setDisable(busy);
        if (busy) {
            undoButton.setDisable(true);
        } else {
            undoButton.setDisable(!service.canUndo());
        }
    }

    private void report(String messageKey, FolderService.BatchResult result) {
        String text = I18n.trn(messageKey, result.succeeded().size());
        if (!result.failed().isEmpty()) {
            Map.Entry<Path, String> first = result.failed().entrySet().iterator().next();
            text += ", " + I18n.tr("status.failed", result.failed().size(), first.getKey(), first.getValue());
        }
        status.setText(text);
    }

    private void chooseOverlay() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.tr("filechooser.title"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(I18n.tr("filechooser.images"),
                        "*.svg", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                new FileChooser.ExtensionFilter(I18n.tr("filechooser.svg"), "*.svg"),
                new FileChooser.ExtensionFilter(I18n.tr("filechooser.all"), "*.*"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            overlay = OverlayImageLoader.load(file.toPath());
            clearLibraryIcon();
            updatePreview();
        } catch (Exception ex) {
            status.setText(I18n.tr("status.cannotReadImage", ex.getMessage()));
        }
    }

    // ---------- Icon library ----------

    /** Called by the gallery when the user clicks an icon. */
    private void pickLibraryIcon(IconLibrary.Entry entry, IconLibrary.Style style) {
        libraryIcon = entry;
        libraryStyle = style;
        libraryIconActive.set(true);
        renderLibraryOverlay();
    }

    /** Rasterizes the chosen library icon in the current icon color and makes it the overlay. */
    private void renderLibraryOverlay() {
        if (libraryIcon == null) {
            return;
        }
        try {
            overlay = IconLibrary.get().render(
                    libraryIcon, libraryStyle, ColorSelector.toHex(iconColors.getValue()), 512);
            updatePreview();
        } catch (Exception ex) {
            status.setText(I18n.tr("status.cannotLoadIcon", ex.getMessage()));
        }
    }

    /** The overlay no longer comes from the library: the icon color does not apply to it. */
    private void clearLibraryIcon() {
        libraryIcon = null;
        libraryIconActive.set(false);
    }

    // ---------- Menu ----------

    private MenuBar buildMenuBar() {
        MenuItem install = menuItem("menu.tools.install");
        install.setOnAction(e -> toggleContextMenu(true));
        MenuItem uninstall = menuItem("menu.tools.uninstall");
        uninstall.setOnAction(e -> toggleContextMenu(false));
        MenuItem cleanup = menuItem("menu.tools.cleanup");
        cleanup.setOnAction(e -> confirmCleanup());
        Menu tools = new Menu();
        tools.textProperty().bind(I18n.binding("menu.tools"));
        tools.getItems().addAll(install, uninstall, new SeparatorMenuItem(), cleanup);

        Menu language = new Menu();
        language.textProperty().bind(I18n.binding("menu.language"));
        ToggleGroup languages = new ToggleGroup();
        for (I18n.Language lang : I18n.Language.values()) {
            RadioMenuItem item = new RadioMenuItem(lang.nativeName());
            item.setToggleGroup(languages);
            item.setSelected(lang == I18n.language());
            item.setOnAction(e -> {
                I18n.setLanguage(lang);
                settings.setLanguage(lang.code());
            });
            language.getItems().add(item);
        }

        MenuItem about = new MenuItem();
        about.textProperty().bind(I18n.binding("menu.help.about", AppInfo.NAME));
        about.setOnAction(e -> showAbout());
        Menu help = new Menu();
        help.textProperty().bind(I18n.binding("menu.help"));
        help.getItems().add(about);

        return new MenuBar(tools, language, help);
    }

    private void toggleContextMenu(boolean install) {
        try {
            if (install) {
                ContextMenuInstaller.install();
                status.setText(I18n.tr("status.menuAdded"));
            } else {
                ContextMenuInstaller.uninstall();
                status.setText(I18n.tr("status.menuRemoved"));
            }
        } catch (Exception ex) {
            status.setText(I18n.tr("status.error", ex.getMessage()));
        }
    }

    private void confirmCleanup() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, I18n.tr("dialog.cleanup.text"),
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(I18n.tr("dialog.cleanup.header"));
        confirm.initOwner(stage);
        confirm.getDialogPane().getStylesheets().add(stylesheet());
        confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            try {
                CleanupService.run();
                Alert done = new Alert(Alert.AlertType.INFORMATION,
                        I18n.tr("dialog.cleanup.done", AppInfo.NAME));
                done.initOwner(stage);
                done.getDialogPane().getStylesheets().add(stylesheet());
                done.showAndWait();
                Platform.exit();
            } catch (Exception ex) {
                status.setText(I18n.tr("status.error", ex.getMessage()));
            }
        });
    }

    private void showAbout() {
        Alert about = new Alert(Alert.AlertType.INFORMATION);
        about.setTitle(I18n.tr("dialog.about.title", AppInfo.NAME));
        about.setHeaderText(AppInfo.NAME + " " + AppInfo.VERSION);
        String text = I18n.tr("dialog.about.text") + "\n\n" + I18n.tr("dialog.about.credits");
        if (!AppInfo.REPO_URL.isBlank()) {
            text += "\n\n" + AppInfo.REPO_URL;
        }
        about.setContentText(text);
        about.setGraphic(new ImageView(appIcon(64)));
        about.initOwner(stage);
        about.getDialogPane().getStylesheets().add(stylesheet());
        about.showAndWait();
    }

    // ---------- Preview ----------

    private void updatePreview() {
        FolderFill fill = currentFill();
        double scale = scaleSlider.getValue();
        // Rendered at twice the size and scaled down: stays sharp on HiDPI screens
        preview.setImage(SwingFXUtils.toFXImage(
                FolderIconRenderer.render(PREVIEW_SIZE * 2, fill, overlay, scale), null));
        // The small ones are drawn at their real pixel size, as Explorer will show them
        for (int i = 0; i < smallPreviews.size(); i++) {
            smallPreviews.get(i).setImage(SwingFXUtils.toFXImage(
                    FolderIconRenderer.render(SMALL_PREVIEWS[i], fill, overlay, scale), null));
        }
    }

    private static Image appIcon(int size) {
        return SwingFXUtils.toFXImage(AppIcon.render(size), null);
    }

    // ---------- Small UI helpers ----------

    private static String stylesheet() {
        return MainApp.class.getResource("/foldermania.css").toExternalForm();
    }

    private static Button button(String key) {
        Button button = new Button();
        button.textProperty().bind(I18n.binding(key));
        return button;
    }

    private static MenuItem menuItem(String key) {
        MenuItem item = new MenuItem();
        item.textProperty().bind(I18n.binding(key));
        return item;
    }

    private static Label label(String key) {
        Label label = new Label();
        label.textProperty().bind(I18n.binding(key));
        return label;
    }

    private static Label title(String key) {
        Label label = label(key);
        label.getStyleClass().add("card-title");
        return label;
    }

    private static Label sectionTitle(String key) {
        Label label = label(key);
        label.getStyleClass().add("section-title");
        return label;
    }

    private static Label mutedLabel(String key) {
        Label label = label(key);
        label.getStyleClass().add("muted");
        return label;
    }

    private static Tooltip tooltip(String key) {
        Tooltip tooltip = new Tooltip();
        tooltip.textProperty().bind(I18n.binding(key));
        return tooltip;
    }

    /** A rounded white panel. */
    private static VBox card(Node... children) {
        VBox box = new VBox(12, children);
        box.getStyleClass().add("card");
        return box;
    }

    /** A button of a segmented switch. position is "first", "middle" or "last". */
    private static ToggleButton segment(String key, String position) {
        ToggleButton button = new ToggleButton();
        button.textProperty().bind(I18n.binding(key));
        button.getStyleClass().addAll("segment", position);
        return button;
    }

    private static ScrollPane scrolling(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    /** A toggle group always keeps one button selected: clicking the selected one does nothing. */
    private static void keepOneSelected(ToggleGroup group) {
        group.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) {
                old.setSelected(true);
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
