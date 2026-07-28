package com.azkar.components.library.ui.state;

import java.io.IOException;
import java.util.ResourceBundle;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.VBox;

public class LibraryLoadingStateComponent extends VBox {

    private LibraryLoadingStateComponent(String bundleName) {
        var loadedBundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/library/library_loading_state_component.fxml"),
                loadedBundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load library loading state component", exception);
        }
    }

    public LibraryLoadingStateComponent() {
        this("com.azkar.i18n.home");
    }
}
