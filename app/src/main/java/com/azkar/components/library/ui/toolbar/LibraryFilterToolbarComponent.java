package com.azkar.components.library.ui.toolbar;

import com.azkar.components.library.model.LibraryCollectionOption;
import java.io.IOException;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import lombok.Getter;
import org.controlsfx.control.ToggleSwitch;

@Getter
public class LibraryFilterToolbarComponent extends HBox {

    @FXML
    private TextField searchField;

    @FXML
    private ToggleSwitch favoritesOnlyToggle;

    @FXML
    private ComboBox<LibraryCollectionOption> collectionCombo;

    @FXML
    private ToggleGroup viewModeGroup;

    @FXML
    private ToggleButton listViewToggle;

    @FXML
    private ToggleButton gridViewToggle;

    private LibraryFilterToolbarComponent(String bundleName) {
        var loadedBundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/library/library_filter_toolbar_component.fxml"),
                loadedBundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load library filter toolbar component", exception);
        }
    }

    public LibraryFilterToolbarComponent() {
        this("com.azkar.i18n.home");
    }
}
