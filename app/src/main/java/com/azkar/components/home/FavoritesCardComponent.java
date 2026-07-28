package com.azkar.components.home;

import com.azkar.i18n.AppLocale;
import java.io.IOException;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;
import lombok.Setter;
import org.kordamp.ikonli.javafx.FontIcon;

public class FavoritesCardComponent extends BorderPane {

    @Setter
    private Runnable onBrowseAction;
    @Setter
    private Runnable onPreviousAction;
    @Setter
    private Runnable onNextAction;

    @FXML
    private Text favoriteArabicText;

    @FXML
    private Text favoriteEnglishText;

    @FXML
    private Text favoriteSourceText;

    @FXML
    private Button browseFavoriteButton;

    @FXML
    private Button previousFavoriteButton;

    @FXML
    private Button nextFavoriteButton;

    @FXML
    private FontIcon browseArrowIcon;

    @FXML
    private FontIcon previousIcon;

    @FXML
    private FontIcon nextIcon;

    public FavoritesCardComponent() {
        super();
        var bundle = ResourceBundle.getBundle("com.azkar.i18n.home");
        var fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/home/favorites_card_component.fxml"),
                bundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load FXML", e);
        }
        configureButtonActions();
        configureDirectionalIcons();
    }

    private void configureButtonActions() {
        if (browseFavoriteButton != null && onBrowseAction != null) {
            browseFavoriteButton.setOnAction(e -> onBrowseAction.run());
        }
        if (previousFavoriteButton != null && onPreviousAction != null) {
            previousFavoriteButton.setOnAction(e -> onPreviousAction.run());
        }
        if (nextFavoriteButton != null && onNextAction != null) {
            nextFavoriteButton.setOnAction(e -> onNextAction.run());
        }
    }

    private void configureDirectionalIcons() {
        boolean isRtl = AppLocale.isCurrentRtl();
        browseArrowIcon.setIconLiteral(isRtl ? "fas-arrow-left" : "fas-arrow-right");
        previousIcon.setIconLiteral(isRtl ? "fas-chevron-right" : "fas-chevron-left");
        nextIcon.setIconLiteral(isRtl ? "fas-chevron-left" : "fas-chevron-right");
    }

    public void setFavoriteArabicText(String text) {
        favoriteArabicText.setText(text);
    }

    public void setFavoriteEnglishText(String text) {
        favoriteEnglishText.setText(text);
    }

    public void setFavoriteSourceText(String text) {
        favoriteSourceText.setText(text);
    }

    public void setNavigationEnabled(boolean enabled) {
        previousFavoriteButton.setDisable(!enabled);
        nextFavoriteButton.setDisable(!enabled);
    }
}