package com.azkar.components.library.ui.toolbar;

import com.azkar.components.library.model.LibrarySortOption;
import java.io.IOException;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import lombok.Getter;

@Getter
public class LibraryMetaRowComponent extends HBox {

    @FXML
    private Label resultCountLabel;

    @FXML
    private ChoiceBox<LibrarySortOption> sortChoice;

    private LibraryMetaRowComponent(String bundleName) {
        var loadedBundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/library/library_meta_row_component.fxml"), loadedBundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load library meta row component", exception);
        }
    }

    public LibraryMetaRowComponent() {
        this("com.azkar.i18n.home");
    }
}
