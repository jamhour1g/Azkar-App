package com.azkar.components.library.ui.toolbar;

import java.io.IOException;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.HBox;
import lombok.Setter;

@Setter
@SuppressWarnings("unused")
public class LibraryHeaderComponent extends HBox {

    private Runnable onRefreshAction;

    private LibraryHeaderComponent(String bundleName) {
        var loadedBundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/library/library_header_component.fxml"), loadedBundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load library header component", exception);
        }
    }

    public LibraryHeaderComponent() {
        this("com.azkar.i18n.home");
    }

    @FXML
    private void onRefreshClicked() {
        if (onRefreshAction != null) {
            onRefreshAction.run();
        }
    }
}
