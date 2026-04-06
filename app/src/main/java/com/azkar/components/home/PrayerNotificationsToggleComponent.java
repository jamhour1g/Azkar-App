package com.azkar.components.home;

import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.HBox;
import lombok.SneakyThrows;
import org.controlsfx.control.ToggleSwitch;

public class PrayerNotificationsToggleComponent extends HBox {

    @FXML
    private ToggleSwitch togglePrayerNotifications;

    @SneakyThrows
    private PrayerNotificationsToggleComponent(String bundleName) {
        var bundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/home/prayer_notifications_toggle_component.fxml"),
                bundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        fxmlLoader.load();
    }

    public PrayerNotificationsToggleComponent() {
        this("com.azkar.i18n.home");
    }
}
