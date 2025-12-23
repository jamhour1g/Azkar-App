package com.azkar.components.home;

import java.util.ResourceBundle;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.VBox;
import lombok.SneakyThrows;

public class GreetingComponent extends VBox {

    @SneakyThrows
    private GreetingComponent(String bundleName) {
        var loadedBundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader = new FXMLLoader(
            getClass().getResource(
                "/com/azkar/components/home/greeting_component.fxml"
            ),
            loadedBundle
        );
        fxmlLoader.setController(this);
        fxmlLoader.setRoot(this);
        fxmlLoader.load();
    }

    public GreetingComponent() {
        this("com.azkar.i18n.home");
    }
}
