package com.azkar.components.home;

import java.util.ResourceBundle;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import lombok.SneakyThrows;

public class PrayerRowComponent extends HBox {

    @FXML
    private Text prayerName;

    @FXML
    private Text prayerTime;

    @FXML
    private Text prayerValue;

    @SneakyThrows
    private PrayerRowComponent(String bundleName) {
        var bundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader = new FXMLLoader(
            getClass().getResource(
                "/com/azkar/components/home/prayer_row_component.fxml"
            ),
            bundle
        );
        fxmlLoader.setController(this);
        fxmlLoader.setRoot(this);
        fxmlLoader.load();
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
}
