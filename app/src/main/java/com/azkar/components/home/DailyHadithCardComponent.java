package com.azkar.components.home;

import java.util.ResourceBundle;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.VBox;
import lombok.SneakyThrows;

public class DailyHadithCardComponent extends VBox {

    @SneakyThrows
    public DailyHadithCardComponent(String bundleName) {
        var loadedBundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/home/daily_hadith_card_component.fxml"), loadedBundle);
        fxmlLoader.setController(this);
        fxmlLoader.setRoot(this);
        fxmlLoader.load();
    }

    public DailyHadithCardComponent() {
        this("com.azkar.i18n.home");
    }
}
