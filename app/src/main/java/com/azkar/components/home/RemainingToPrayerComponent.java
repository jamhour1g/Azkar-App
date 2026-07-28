package com.azkar.components.home;

import java.io.IOException;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import lombok.val;

public class RemainingToPrayerComponent extends VBox {

    @FXML
    private Text countdownText;

    @FXML
    private Text locationText;

    public RemainingToPrayerComponent() {
        val bundle = ResourceBundle.getBundle("com.azkar.i18n.home");
        val fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/home/remaining_to_prayer_component.fxml"),
                bundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load FXML", e);
        }
    }


}
