package com.azkar.components.home;

import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import lombok.SneakyThrows;
import org.controlsfx.control.ToggleSwitch;

public class HomeComponent extends ScrollPane {

    @FXML
    private ToggleSwitch togglePrayerNotifications;

    @FXML
    private ToggleButton fajrPrayerNotificationToggle;

    @FXML
    private ToggleButton dhuhrPrayerNotificationToggle;

    @FXML
    private ToggleButton asrPrayerNotificationToggle;

    @FXML
    private ToggleButton maghribPrayerNotificationToggle;

    @FXML
    private ToggleButton ishaPrayerNotificationToggle;

    @SneakyThrows
    private HomeComponent(String bundleName) {
        var loadedBundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader = new FXMLLoader(
            getClass().getResource(
                "/com/azkar/components/home/home_component.fxml"
            ),
            loadedBundle
        );
        fxmlLoader.setController(this);
        fxmlLoader.setRoot(this);
        fxmlLoader.load();
    }

    public HomeComponent() {
        this("com.azkar.i18n.home");
    }
}
