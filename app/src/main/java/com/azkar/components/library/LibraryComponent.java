package com.azkar.components.library;

import java.util.ResourceBundle;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ScrollPane;
import lombok.SneakyThrows;

public class LibraryComponent extends ScrollPane {

    @SneakyThrows
    private LibraryComponent(String bundleName) {
        var loadedBundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/library/library_component.fxml"), loadedBundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        fxmlLoader.load();
    }

    public LibraryComponent() {
        this("com.azkar.i18n.home");
    }
}
