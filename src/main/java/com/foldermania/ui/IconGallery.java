package com.foldermania.ui;

import com.foldermania.icon.IconLibrary;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Icon picker. Collapsed, it shows a compact grid of the most used icons; expanded, it shows the
 * whole library with search, category filter and an outline/filled switch.
 * The long list is virtualized (one ListView row = one row of icons) and thumbnails are rendered
 * lazily and cached, so the 5000+ icons cost nothing until they scroll into view.
 */
public final class IconGallery extends VBox {

    private static final int COMPACT_COUNT = 32;
    private static final int COLUMNS = 8;
    private static final int CELL = 40;
    private static final int THUMB = 28;
    private static final String THUMB_COLOR = "#3C3C43";

    private final IconLibrary library = IconLibrary.get();
    private final BiConsumer<IconLibrary.Entry, IconLibrary.Style> onPick;

    private final FlowPane compactBox = new FlowPane(4, 4);
    private final VBox expandedBox = new VBox(8);
    private final TextField searchField = new TextField();
    private final ComboBox<String> categoryBox = new ComboBox<>();
    private final ToggleButton outlineButton = new ToggleButton("Outline");
    private final ToggleButton filledButton = new ToggleButton("Filled");
    private final ListView<List<IconLibrary.Entry>> list = new ListView<>();
    private final Label countLabel = new Label();
    private final Button expandButton = new Button();

    /** Least recently used thumbnails are dropped once the cache holds 800 of them. */
    private final Map<String, Image> thumbnails = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > 800;
        }
    };

    /**
     * @param onPick called with the chosen icon and the style (outline or filled) it was chosen in
     */
    public IconGallery(BiConsumer<IconLibrary.Entry, IconLibrary.Style> onPick) {
        super(8);
        this.onPick = onPick;

        buildCompactBox();
        buildExpandedBox();

        expandButton.setOnAction(e -> setExpanded(!expandedBox.isVisible()));
        setExpanded(false);

        setAlignment(Pos.TOP_CENTER);
        getChildren().addAll(compactBox, expandedBox, expandButton);
    }

    // ---------- Building ----------

    private void buildCompactBox() {
        compactBox.setAlignment(Pos.CENTER);
        int shown = 0;
        for (IconLibrary.Entry entry : library.popular()) {
            if (!entry.has(IconLibrary.Style.OUTLINE)) {
                continue;
            }
            compactBox.getChildren().add(iconButton(entry, IconLibrary.Style.OUTLINE));
            if (++shown >= COMPACT_COUNT) {
                break;
            }
        }
    }

    private void buildExpandedBox() {
        searchField.setPromptText("Search all icons (e.g. music, folder, game)...");
        searchField.textProperty().addListener((obs, old, val) -> refresh());

        categoryBox.getItems().add(IconLibrary.POPULAR);
        categoryBox.getItems().add(IconLibrary.ALL);
        categoryBox.getItems().addAll(library.categories());
        categoryBox.setValue(IconLibrary.POPULAR);
        categoryBox.valueProperty().addListener((obs, old, val) -> refresh());

        // Exactly one style is always selected
        ToggleGroup styleGroup = new ToggleGroup();
        outlineButton.setToggleGroup(styleGroup);
        filledButton.setToggleGroup(styleGroup);
        outlineButton.setSelected(true);
        styleGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) {
                old.setSelected(true);
            } else {
                refresh();
            }
        });

        list.setFixedCellSize(CELL + 4);
        list.setPrefHeight(300);
        // The list is a picker, not a selection list: hide the row selection and the zebra stripes
        list.setStyle("-fx-selection-bar: transparent; -fx-selection-bar-non-focused: transparent;"
                + " -fx-control-inner-background-alt: -fx-control-inner-background;");
        list.setCellFactory(lv -> new ListCell<>() {
            private final HBox box = new HBox(4);

            {
                box.setAlignment(Pos.CENTER_LEFT);
                setPadding(new Insets(2, 4, 2, 4));
            }

            @Override
            protected void updateItem(List<IconLibrary.Entry> row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                box.getChildren().clear();
                for (IconLibrary.Entry entry : row) {
                    box.getChildren().add(iconButton(entry, currentStyle()));
                }
                setGraphic(box);
            }
        });

        HBox filters = new HBox(8, categoryBox, outlineButton, filledButton);
        filters.setAlignment(Pos.CENTER_LEFT);
        expandedBox.getChildren().addAll(searchField, filters, list, countLabel);
    }

    // ---------- Behaviour ----------

    private void setExpanded(boolean expanded) {
        compactBox.setVisible(!expanded);
        compactBox.setManaged(!expanded);
        expandedBox.setVisible(expanded);
        expandedBox.setManaged(expanded);
        expandButton.setText(expanded
                ? "Show fewer icons"
                : "Show all icons (" + library.size() + ")");
        if (expanded) {
            refresh();
        }
    }

    private IconLibrary.Style currentStyle() {
        return filledButton.isSelected() ? IconLibrary.Style.FILLED : IconLibrary.Style.OUTLINE;
    }

    /** Re-runs the search and rebuilds the rows of the list. */
    private void refresh() {
        String category = categoryBox.getValue();
        // A search always looks in the whole library, not only in the popular subset
        if (IconLibrary.POPULAR.equals(category) && !searchField.getText().isBlank()) {
            category = IconLibrary.ALL;
        }
        List<IconLibrary.Entry> results = library.search(searchField.getText(), category, currentStyle());

        List<List<IconLibrary.Entry>> rows = new ArrayList<>();
        for (int i = 0; i < results.size(); i += COLUMNS) {
            rows.add(results.subList(i, Math.min(i + COLUMNS, results.size())));
        }
        list.getItems().setAll(rows);
        list.scrollTo(0);
        countLabel.setText(results.size() + " icons");
    }

    private Button iconButton(IconLibrary.Entry entry, IconLibrary.Style style) {
        Button button = new Button();
        button.setMinSize(CELL, CELL);
        button.setPrefSize(CELL, CELL);
        button.setMaxSize(CELL, CELL);
        button.setGraphic(thumbnail(entry, style));
        button.setTooltip(new Tooltip(entry.label()));
        button.setOnAction(e -> onPick.accept(entry, style));
        return button;
    }

    private ImageView thumbnail(IconLibrary.Entry entry, IconLibrary.Style style) {
        String key = style + "/" + entry.name();
        Image image = thumbnails.get(key);
        if (image == null) {
            try {
                // Rendered at twice the display size so it stays sharp on HiDPI screens
                BufferedImage rendered = library.render(entry, style, THUMB_COLOR, THUMB * 2);
                image = SwingFXUtils.toFXImage(rendered, null);
                thumbnails.put(key, image);
            } catch (IOException ex) {
                return new ImageView();
            }
        }
        ImageView view = new ImageView(image);
        view.setFitWidth(THUMB);
        view.setFitHeight(THUMB);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }
}
