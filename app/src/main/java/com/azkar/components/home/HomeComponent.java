package com.azkar.components.home;

import java.util.ResourceBundle;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ScrollPane;
import lombok.SneakyThrows;

public class HomeComponent extends ScrollPane {

    @SneakyThrows
    private HomeComponent(String bundleName) {
        var loadedBundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader =
                new FXMLLoader(getClass().getResource("/com/azkar/components/home/home_component.fxml"), loadedBundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        fxmlLoader.load();
    }

    public HomeComponent() {
        this("com.azkar.i18n.home");
    }
}
