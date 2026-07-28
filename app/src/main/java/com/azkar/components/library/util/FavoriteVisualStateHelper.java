package com.azkar.components.library.util;

import java.util.ResourceBundle;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.javafx.FontIcon;

public final class FavoriteVisualStateHelper {

    private static final String FAVORITE_BUTTON_STYLE = "library-icon-btn-favorite";
    private static final String FAVORITE_ICON_STYLE = "library-icon-favorite";
    private static final String DEFAULT_ICON_STYLE = "library-icon";
    private static final String FAVORITE_ICON_LITERAL = "fas-star";
    private static final String NON_FAVORITE_ICON_LITERAL = "far-star";

    private FavoriteVisualStateHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void apply(
            boolean favorite, Button favoriteButton, FontIcon favoriteIcon, ResourceBundle bundle) {
        favoriteButton.getStyleClass().remove(FAVORITE_BUTTON_STYLE);
        if (favorite) {
            favoriteButton.getStyleClass().add(FAVORITE_BUTTON_STYLE);
            favoriteIcon.setIconLiteral(FAVORITE_ICON_LITERAL);
            favoriteIcon.getStyleClass().setAll(FAVORITE_ICON_STYLE);
            updateTooltip(favoriteButton, bundle.getString("libraryActionUnfavorite"));
            return;
        }

        favoriteIcon.setIconLiteral(NON_FAVORITE_ICON_LITERAL);
        favoriteIcon.getStyleClass().setAll(DEFAULT_ICON_STYLE);
        updateTooltip(favoriteButton, bundle.getString("libraryActionFavorite"));
    }

    private static void updateTooltip(Button button, String text) {
        Tooltip tooltip = button.getTooltip();
        if (tooltip != null) {
            tooltip.setText(text);
        }
    }
}
