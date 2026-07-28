package com.azkar.components.home;

import java.io.IOException;
import java.util.ResourceBundle;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import org.kordamp.ikonli.javafx.FontIcon;

public class PrayerRowComponent extends HBox {

    private final BooleanProperty nextPrayer = new SimpleBooleanProperty(this, "nextPrayer", false);
    private final BooleanProperty notificationEnabled = new SimpleBooleanProperty(this, "notificationEnabled", false);

    @FXML
    private Text prayerName;

    @FXML
    private Text prayerTime;

    @FXML
    private Text prayerValue;

    @FXML
    private FontIcon prayerIcon;

    @FXML
    private ToggleButton prayerNotificationToggle;

    @FXML
    private FontIcon notificationIcon;

    public PrayerRowComponent() {
        super();
        var bundle = ResourceBundle.getBundle("com.azkar.i18n.home");
        var fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/home/prayer_row_component.fxml"),
                bundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load FXML", e);
        }
        nextPrayer.addListener((observable, oldValue, newValue) -> applyNextPrayerStyle(newValue));
        notificationEnabled.addListener((observable, oldValue, enabled) -> applyNotificationStyle(enabled));
        applyStyles();
        if (prayerNotificationToggle != null) {
            prayerNotificationToggle.setOnAction(event -> setNotificationEnabled(prayerNotificationToggle.isSelected()));
        }
    }

    private void applyStyles() {
        applyNextPrayerStyle(nextPrayer.get());
        applyNotificationStyle(notificationEnabled.get());
    }

    public void setPrayerName(String prayerName) {
        this.prayerName.setText(prayerName);
    }

    public String getPrayerName() {
        return prayerName.getText();
    }

    public void setPrayerTime(String prayerTime) {
        this.prayerTime.setText(prayerTime);
    }

    public String getPrayerTime() {
        return prayerTime.getText();
    }

    public void setPrayerValue(String prayerValue) {
        this.prayerValue.setText(prayerValue);
    }

    public String getPrayerValue() {
        return prayerValue.getText();
    }

    public boolean isNextPrayer() {
        return nextPrayer.get();
    }

    public void setNextPrayer(boolean nextPrayer) {
        this.nextPrayer.set(nextPrayer);
    }

    public void setPrayerIcon(String iconLiteral) {
        if (prayerIcon != null && iconLiteral != null && !iconLiteral.isBlank()) {
            prayerIcon.setIconLiteral(iconLiteral);
        }
    }

    public boolean isNotificationEnabled() {
        return notificationEnabled.get();
    }

    public void setNotificationEnabled(boolean enabled) {
        notificationEnabled.set(enabled);
    }

    public BooleanProperty notificationEnabledProperty() {
        return notificationEnabled;
    }

    public void setNotificationVisible(boolean visible) {
        if (prayerNotificationToggle == null) return;
        prayerNotificationToggle.setManaged(visible);
        prayerNotificationToggle.setVisible(visible);
    }

    private void applyNextPrayerStyle(boolean active) {
        if (active) {
            if (!getStyleClass().contains("next-prayer")) {
                getStyleClass().add("next-prayer");
            }
        } else {
            getStyleClass().remove("next-prayer");
        }
    }

    private void applyNotificationStyle(boolean enabled) {
        if (prayerNotificationToggle != null && prayerNotificationToggle.isSelected() != enabled) {
            prayerNotificationToggle.setSelected(enabled);
        }
        if (notificationIcon != null) {
            notificationIcon.setIconLiteral(enabled ? "fas-bell" : "far-bell");
        }
    }
}