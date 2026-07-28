package com.azkar.components.library.ui.browse;

import com.azkar.components.library.model.LibraryRemembranceRow;
import java.util.ResourceBundle;
import java.util.function.LongConsumer;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.javafx.FontIcon;

final class LibraryTableActionsCell extends TableCell<LibraryRemembranceRow, LibraryRemembranceRow> {

    private final ResourceBundle bundle;
    private final LongConsumer readAction;
    private final LongConsumer collectionAction;
    private final LongConsumer favoriteAction;

    private final FontIcon readIcon = buildActionIcon("fas-book-open");
    private final FontIcon collectionIcon = buildActionIcon("fas-layer-group");
    private final FontIcon favoriteIcon = buildActionIcon("far-star");
    private final Tooltip readTooltip;
    private final Tooltip collectionTooltip;
    private final Tooltip favoriteTooltip;
    private final Button readButton;
    private final Button collectionButton;
    private final Button favoriteButton;
    private final HBox actionsBox;

    LibraryTableActionsCell(
            ResourceBundle bundle,
            LongConsumer readAction,
            LongConsumer collectionAction,
            LongConsumer favoriteAction) {
        this.bundle = bundle;
        this.readAction = readAction;
        this.collectionAction = collectionAction;
        this.favoriteAction = favoriteAction;

        readTooltip = new Tooltip(bundle.getString("libraryActionRead"));
        collectionTooltip = new Tooltip(bundle.getString("libraryActionAddCollection"));
        favoriteTooltip = new Tooltip(bundle.getString("libraryActionFavorite"));
        readButton = buildIconActionButton(readIcon, readTooltip);
        collectionButton = buildIconActionButton(collectionIcon, collectionTooltip);
        favoriteButton = buildIconActionButton(favoriteIcon, favoriteTooltip);
        actionsBox = new HBox(4, readButton, collectionButton, favoriteButton);

        actionsBox.getStyleClass().add("library-table-actions");
        actionsBox.setFillHeight(false);
        actionsBox.setMinWidth(Region.USE_PREF_SIZE);
        actionsBox.setMaxWidth(Region.USE_PREF_SIZE);

        readButton.setOnAction(event -> {
            LibraryRemembranceRow row = getItem();
            if (row != null) {
                this.readAction.accept(row.id());
            }
        });
        collectionButton.setOnAction(event -> {
            LibraryRemembranceRow row = getItem();
            if (row != null) {
                this.collectionAction.accept(row.id());
            }
        });
        favoriteButton.setOnAction(event -> {
            LibraryRemembranceRow row = getItem();
            if (row != null) {
                this.favoriteAction.accept(row.id());
            }
        });
    }

    @Override
    protected void updateItem(LibraryRemembranceRow row, boolean empty) {
        super.updateItem(row, empty);
        if (empty || row == null) {
            setGraphic(null);
            return;
        }

        applyFavoriteActionVisualState(row.favorite());
        setGraphic(actionsBox);
    }

    private static FontIcon buildActionIcon(String iconLiteral) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(12);
        icon.getStyleClass().setAll("library-icon");
        return icon;
    }

    private static Button buildIconActionButton(FontIcon icon, Tooltip tooltip) {
        Button button = new Button();
        button.setMnemonicParsing(false);
        button.setGraphic(icon);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setTooltip(tooltip);
        button.getStyleClass().add("library-icon-btn");
        button.setMinSize(28, 28);
        button.setPrefSize(28, 28);
        button.setMaxSize(28, 28);
        return button;
    }

    private void applyFavoriteActionVisualState(boolean favorite) {
        favoriteButton.getStyleClass().remove("library-icon-btn-favorite");
        if (favorite) {
            favoriteButton.getStyleClass().add("library-icon-btn-favorite");
            favoriteIcon.setIconLiteral("fas-star");
            favoriteIcon.getStyleClass().setAll("library-icon-favorite");
            favoriteTooltip.setText(bundle.getString("libraryActionUnfavorite"));
            return;
        }

        favoriteIcon.setIconLiteral("far-star");
        favoriteIcon.getStyleClass().setAll("library-icon");
        favoriteTooltip.setText(bundle.getString("libraryActionFavorite"));
    }
}