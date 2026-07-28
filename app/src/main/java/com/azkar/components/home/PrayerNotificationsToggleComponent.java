package com.azkar.components.home;

import java.io.IOException;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.HBox;
import lombok.Setter;
import org.controlsfx.control.ToggleSwitch;

public class PrayerNotificationsToggleComponent extends HBox {

    @FXML
    private ToggleSwitch togglePrayerNotifications;

    @Setter
    private Consumer<Boolean> onToggleAction;
    private boolean updatingToggle;

    public PrayerNotificationsToggleComponent() {
        super();
        var bundle = ResourceBundle.getBundle("com.azkar.i18n.home");
        var fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/home/prayer_notifications_toggle_component.fxml"),
                bundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load FXML", e);
        }
        togglePrayerNotifications.selectedProperty().addListener((observable, oldValue, selected) -> {
            if (updatingToggle) {
                return;
            }
            if (onToggleAction != null) {
                onToggleAction.accept(selected);
            }
        });
    }

    public boolean isNotificationsEnabled() {
        return togglePrayerNotifications.isSelected();
    }

    public void setNotificationsEnabled(boolean enabled) {
        updatingToggle = true;
        try {
            togglePrayerNotifications.setSelected(enabled);
        } finally {
            updatingToggle = false;
        }
    }
}