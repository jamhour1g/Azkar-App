package com.azkar.components.home;

import java.util.ResourceBundle;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.BorderPane;
import lombok.SneakyThrows;

public class FavoritesCardComponent extends BorderPane {

    @SneakyThrows
    private FavoritesCardComponent(String bundleName) {
        var bundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/home/favorites_card_component.fxml"), bundle);
        fxmlLoader.setController(this);
        fxmlLoader.setRoot(this);
        fxmlLoader.load();
    }

    public FavoritesCardComponent() {
        this("com.azkar.i18n.home");
    }
}
