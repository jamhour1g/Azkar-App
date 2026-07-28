package com.azkar.components.library.ui.detail;

import java.io.IOException;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import org.kordamp.ikonli.javafx.FontIcon;

public class LibraryDetailPaneComponent extends VBox {

    @Setter
    private Runnable onReadAction;

    @Setter
    private Runnable onCollectionAction;

    @Setter
    private Runnable onFavoriteAction;

    @Getter
    @FXML
    private Label detailCategoryLabel;

    @Getter
    @FXML
    private Label detailArabicLabel;

    @Getter
    @FXML
    private Label detailEnglishLabel;

    @Getter
    @FXML
    private Label detailSourceLabel;

    @Getter
    @FXML
    private Button detailReadButton;

    @Getter
    @FXML
    private Button detailCollectionButton;

    @Getter
    @FXML
    private Button detailFavoriteButton;

    @Getter
    @FXML
    private FontIcon detailFavoriteIcon;

    @Getter
    @FXML
    private LibraryReminderPanelComponent reminderPanel;

    private LibraryDetailPaneComponent(String bundleName) {
        var loadedBundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/library/library_detail_pane_component.fxml"),
                loadedBundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load library detail pane component", exception);
        }
    }

    public LibraryDetailPaneComponent() {
        this("com.azkar.i18n.home");
    }

    @FXML
    private void onReadClicked() {
        if (onReadAction != null) {
            onReadAction.run();
        }
    }

    @FXML
    private void onCollectionClicked() {
        if (onCollectionAction != null) {
            onCollectionAction.run();
        }
    }

    @FXML
    private void onFavoriteClicked() {
        if (onFavoriteAction != null) {
            onFavoriteAction.run();
        }
    }
}
