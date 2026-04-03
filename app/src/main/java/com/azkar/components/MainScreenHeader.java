package com.azkar.components;

import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import lombok.SneakyThrows;

public class MainScreenHeader extends HBox {

    @FXML
    private Button homeHeaderBtn;

    @FXML
    private Button azkarLibHeaderBtn;

    @FXML
    private Button settingsBtn;

    @FXML
    private ToggleButton darkModeToggle;

    @SneakyThrows
    private MainScreenHeader(String bundleName) {
        var loadedBundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/home/main_screen_header.fxml"), loadedBundle);
        fxmlLoader.setController(this);
        fxmlLoader.setRoot(this);
        fxmlLoader.load();
    }

    public MainScreenHeader() {
        this("com.azkar.i18n.home");
    }
}
