package com.azkar.components.home;

import java.util.ResourceBundle;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import lombok.SneakyThrows;
import org.kordamp.ikonli.javafx.FontIcon;

public class PrayerRowComponent extends HBox {

    private final BooleanProperty nextPrayer = new SimpleBooleanProperty(this, "nextPrayer", false);

    private String prayerIconLiteral = "far-sun";

    @FXML
    private Text prayerName;

    @FXML
    private Text prayerTime;

    @FXML
    private Text prayerValue;

    @FXML
    private FontIcon prayerIcon;

    @SneakyThrows
    private PrayerRowComponent(String bundleName) {
        var bundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader =
                new FXMLLoader(getClass().getResource("/com/azkar/components/home/prayer_row_component.fxml"), bundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        fxmlLoader.load();

        nextPrayer.addListener((obs, oldValue, newValue) -> applyNextPrayerStyle(newValue));
        setPrayerIcon(prayerIconLiteral);
        applyNextPrayerStyle(nextPrayer.get());
    }

    public PrayerRowComponent() {
        this("com.azkar.i18n.home");
    }

    public void setPrayerName(String prayerName) {
        this.prayerName.setText(prayerName);
    }

    public String getPrayerName() {
        return prayerName.getText();
    }

    public StringProperty prayerNameProperty() {
        return prayerName.textProperty();
    }

    public void setPrayerTime(String prayerTime) {
        this.prayerTime.setText(prayerTime);
    }

    public String getPrayerTime() {
        return prayerTime.getText();
    }

    public StringProperty prayerTimeProperty() {
        return prayerTime.textProperty();
    }

    public void setPrayerValue(String prayerValue) {
        this.prayerValue.setText(prayerValue);
    }

    public String getPrayerValue() {
        return prayerValue.getText();
    }

    public StringProperty prayerValueProperty() {
        return prayerValue.textProperty();
    }

    public boolean isNextPrayer() {
        return nextPrayer.get();
    }

    public void setNextPrayer(boolean nextPrayer) {
        this.nextPrayer.set(nextPrayer);
    }

    public void setPrayerIcon(String prayerIcon) {
        prayerIconLiteral = prayerIcon;
        if (this.prayerIcon != null && prayerIcon != null && !prayerIcon.isBlank()) {
            this.prayerIcon.setIconLiteral(prayerIcon);
        }
    }

    public String getPrayerIcon() {
        return prayerIconLiteral;
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
}
