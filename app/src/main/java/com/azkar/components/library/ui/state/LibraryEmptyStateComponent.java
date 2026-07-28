package com.azkar.components.library.ui.state;

import java.io.IOException;
import java.util.ResourceBundle;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.VBox;

public class LibraryEmptyStateComponent extends VBox {

    private LibraryEmptyStateComponent(String bundleName) {
        var loadedBundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/library/library_empty_state_component.fxml"),
                loadedBundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load library empty state component", exception);
        }
    }

    public LibraryEmptyStateComponent() {
        this("com.azkar.i18n.home");
    }
}
