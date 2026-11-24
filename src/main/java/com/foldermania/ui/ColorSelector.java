package com.foldermania.ui;

import com.foldermania.i18n.I18n;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A color chooser: the 33 palette colors (11 families x 3 shades), a button that opens the full
 * color picker for any other color, and the colors used recently. Used for folders and for icons.
 */
public final class ColorSelector extends VBox {

    private static final int SWATCH = 30;

    private final ObjectProperty<Color> value;
    private final List<Button> paletteSwatches = new ArrayList<>();
    private final List<Button> recentSwatches = new ArrayList<>();
    private final FlowPane recentBox = new FlowPane(6, 6);
    private final ColorPicker picker = new ColorPicker();

    public ColorSelector(Color initial) {
        super(12);
        value = new SimpleObjectProperty<>(initial);

        // Palette: one column per color family, shades from dark (top) to light (bottom)
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        for (int col = 0; col < Palette.FAMILIES.size(); col++) {
            Palette.Family family = Palette.FAMILIES.get(col);
            for (int row = 0; row < Palette.Shade.values().length; row++) {
                Palette.Shade shade = Palette.Shade.values()[row];
                Button swatch = swatch(family.hex(shade),
                        () -> I18n.tr("color." + family.name().toLowerCase(Locale.ROOT))
                                + " (" + I18n.tr("shade." + shade.name().toLowerCase(Locale.ROOT)) + ")");
                paletteSwatches.add(swatch);
                grid.add(swatch, col, row);
            }
        }

        // Any color: the standard color picker, bound to the same value
        // The CSS class hides the color name and keeps just the swatch
        picker.getStyleClass().add("custom-color-picker");
        picker.valueProperty().bindBidirectional(value);
        Tooltip pickerTip = new Tooltip();
        pickerTip.textProperty().bind(I18n.binding("color.custom.tip"));
        picker.setTooltip(pickerTip);
        Label customLabel = new Label();
        customLabel.textProperty().bind(I18n.binding("color.custom"));
        HBox customRow = new HBox(10, picker, customLabel);
        customRow.setAlignment(Pos.CENTER_LEFT);

        // Recent colors
        Label recentTitle = new Label();
        recentTitle.textProperty().bind(I18n.binding("color.recent"));
        recentTitle.getStyleClass().add("section-title");
        setRecent(List.of());

        getChildren().addAll(grid, customRow, recentTitle, recentBox);
        value.addListener((obs, old, now) -> updateSelection());
        updateSelection();
    }

    public ObjectProperty<Color> valueProperty() {
        return value;
    }

    public Color getValue() {
        return value.get();
    }

    public void setValue(Color color) {
        value.set(color);
    }

    /** Replaces the list of recent colors (#RRGGBB strings, most recent first). */
    public void setRecent(List<String> hexes) {
        recentSwatches.clear();
        recentBox.getChildren().clear();
        for (String hex : hexes) {
            Button swatch = swatch(hex, () -> hex);
            recentSwatches.add(swatch);
            recentBox.getChildren().add(swatch);
        }
        if (hexes.isEmpty()) {
            Label none = new Label();
            none.textProperty().bind(I18n.binding("color.recent.none"));
            none.getStyleClass().add("muted");
            recentBox.getChildren().add(none);
        }
        updateSelection();
    }

    private Button swatch(String hex, java.util.function.Supplier<String> tooltipText) {
        Button button = new Button();
        button.getStyleClass().add("swatch");
        button.setMinSize(SWATCH, SWATCH);
        button.setPrefSize(SWATCH, SWATCH);
        button.setMaxSize(SWATCH, SWATCH);
        button.setStyle("-fx-background-color: " + hex + ";");
        button.getProperties().put("hex", hex);

        // The tooltip text is read when it is about to show, so it follows the current language
        Tooltip tooltip = new Tooltip();
        tooltip.setOnShowing(e -> tooltip.setText(tooltipText.get()));
        button.setTooltip(tooltip);

        button.setOnAction(e -> value.set(Color.web(hex)));
        return button;
    }

    /** Rings the swatch(es) that match the current color. */
    private void updateSelection() {
        String current = toHex(value.get());
        for (List<Button> list : List.of(paletteSwatches, recentSwatches)) {
            for (Button button : list) {
                boolean selected = current.equalsIgnoreCase((String) button.getProperties().get("hex"));
                button.getStyleClass().remove("selected");
                if (selected) {
                    button.getStyleClass().add("selected");
                }
            }
        }
    }

    // ---------- Color helpers shared by the UI ----------

    public static String toHex(Color c) {
        return String.format(Locale.ROOT, "#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    public static java.awt.Color toAwt(Color c) {
        return new java.awt.Color(
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    public static Color fromAwt(java.awt.Color c) {
        return Color.rgb(c.getRed(), c.getGreen(), c.getBlue());
    }
}
