package com.azkar.components.library.ui.card;

import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.LongConsumer;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.kordamp.ikonli.javafx.FontIcon;

public class RemembranceCardComponent extends VBox {

    private long remembranceId = -1L;
    private LongConsumer onFavoriteAction;
    private LongConsumer onAddToCollectionAction;
    private LongConsumer onReadAction;

    private String favoriteStyleClass = "library-icon-btn-favorite";
    private ResourceBundle bundle;

    @FXML
    private Text categoryText;

    @FXML
    private Text arabicTextNode;

    @FXML
    private Text translationTextNode;

    @FXML
    private Label sourceTextNode;

    @FXML
    private Button favoriteButton;

    @FXML
    private FontIcon favoriteIcon;

    private RemembranceCardComponent(String bundleName) {
        bundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/library/remembrance_card_component.fxml"), bundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load remembrance card component", exception);
        }
    }

    public RemembranceCardComponent() {
        this("com.azkar.i18n.home");
    }

    @FXML
    private void initialize() {
        widthProperty().addListener((observable, oldWidth, newWidth) -> updateWrappingWidth(newWidth.doubleValue()));
        updateWrappingWidth(getWidth());
    }

    public void setRemembranceId(long remembranceId) {
        this.remembranceId = remembranceId;
    }

    public void setCategory(String category) {
        categoryText.setText(localizeCategory(category));
    }

    public void setArabicText(String arabicText) {
        arabicTextNode.setText(arabicText);
    }

    public void setTranslationText(String translationText) {
        translationTextNode.setText(translationText);
    }

    public void setSourceText(String sourceText) {
        sourceTextNode.setText(sourceText);
    }

    public void setFavorite(boolean favorite) {
        favoriteButton.getStyleClass().remove(favoriteStyleClass);
        if (favorite) {
            favoriteButton.getStyleClass().add(favoriteStyleClass);
            favoriteIcon.setIconLiteral("fas-star");
            favoriteIcon.getStyleClass().setAll("library-icon-favorite");
            return;
        }

        favoriteButton.getStyleClass().remove("library-icon-btn-favorite");
        favoriteIcon.setIconLiteral("far-star");
        favoriteIcon.getStyleClass().setAll("library-icon");
    }

    public void setFavoriteStyleClass(String favoriteStyleClass) {
        if (favoriteStyleClass == null || favoriteStyleClass.isBlank()) {
            return;
        }
        this.favoriteStyleClass = favoriteStyleClass;
    }

    public void setOnFavoriteAction(LongConsumer onFavoriteAction) {
        this.onFavoriteAction = onFavoriteAction;
    }

    public void setOnAddToCollectionAction(LongConsumer onAddToCollectionAction) {
        this.onAddToCollectionAction = onAddToCollectionAction;
    }

    public void setOnReadAction(LongConsumer onReadAction) {
        this.onReadAction = onReadAction;
    }

    @FXML
    private void onFavoriteClicked() {
        if (onFavoriteAction != null && remembranceId > 0) {
            onFavoriteAction.accept(remembranceId);
        }
    }

    @FXML
    private void onAddToCollectionClicked() {
        if (onAddToCollectionAction != null && remembranceId > 0) {
            onAddToCollectionAction.accept(remembranceId);
        }
    }

    @FXML
    private void onReadClicked() {
        if (onReadAction != null && remembranceId > 0) {
            onReadAction.accept(remembranceId);
        }
    }

    private void updateWrappingWidth(double width) {
        double wrappingWidth = Math.max(260, width - 72);
        arabicTextNode.setWrappingWidth(wrappingWidth);
        translationTextNode.setWrappingWidth(wrappingWidth);
    }

    private String localizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return category;
        }

        String normalized = category.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("morning")) {
            return bundle.getString("libraryFilterMorning");
        }
        if (normalized.equals("evening")) {
            return bundle.getString("libraryFilterEvening");
        }
        if (normalized.equals("travel")) {
            return bundle.getString("libraryFilterTravel");
        }
        return category;
    }
}
