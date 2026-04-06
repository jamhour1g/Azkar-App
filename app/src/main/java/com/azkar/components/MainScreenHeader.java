package com.azkar.components;

import java.util.ResourceBundle;
import javafx.application.ColorScheme;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import lombok.Setter;
import lombok.SneakyThrows;
import org.kordamp.ikonli.javafx.FontIcon;

public class MainScreenHeader extends HBox {

    public enum HeaderTab {
        HOME,
        LIBRARY
    }

    @Setter
    private Runnable onHomeAction;

    @Setter
    private Runnable onAzkarLibraryAction;

    private boolean updatingThemeToggle;
    private Scene boundScene;
    private ChangeListener<ColorScheme> colorSchemeListener;

    @FXML
    private Button homeHeaderBtn;

    @FXML
    private Button azkarLibHeaderBtn;

    @FXML
    private Button qiblaHeaderBtn;

    @FXML
    private Button settingsBtn;

    @FXML
    private ToggleButton darkModeToggle;

    @FXML
    private FontIcon themeIcon;

    @SneakyThrows
    private MainScreenHeader(String bundleName) {
        var loadedBundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/home/main_screen_header.fxml"), loadedBundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        fxmlLoader.load();

        setActiveTab(HeaderTab.HOME);
        setupThemeToggle();
    }

    public MainScreenHeader() {
        this("com.azkar.i18n.home");
    }

    public void setActiveTab(HeaderTab tab) {
        homeHeaderBtn.getStyleClass().remove("nav-btn-active");
        azkarLibHeaderBtn.getStyleClass().remove("nav-btn-active");

        if (tab == HeaderTab.HOME) {
            homeHeaderBtn.getStyleClass().add("nav-btn-active");
        } else if (tab == HeaderTab.LIBRARY) {
            azkarLibHeaderBtn.getStyleClass().add("nav-btn-active");
        }
    }

    @FXML
    private void onHomeClicked() {
        if (onHomeAction != null) {
            onHomeAction.run();
        }
    }

    @FXML
    private void onAzkarLibraryClicked() {
        if (onAzkarLibraryAction != null) {
            onAzkarLibraryAction.run();
        }
    }

    private void setupThemeToggle() {
        darkModeToggle.selectedProperty().addListener((_, _, isSelected) -> {
            if (!updatingThemeToggle) {
                applyThemeFromToggle(isSelected);
            }
            updateThemeIcon(isSelected);
        });

        sceneProperty().addListener((_, _, newScene) -> bindThemeToggle(newScene));

        if (getScene() != null) {
            bindThemeToggle(getScene());
        } else {
            updateThemeIcon(darkModeToggle.isSelected());
        }
    }

    private void bindThemeToggle(Scene scene) {
        if (boundScene != null && colorSchemeListener != null) {
            boundScene.getPreferences().colorSchemeProperty().removeListener(colorSchemeListener);
        }

        boundScene = scene;
        if (scene == null) {
            return;
        }

        colorSchemeListener = (_, _, newScheme) -> syncThemeToggleFromColorScheme(newScheme);
        scene.getPreferences().colorSchemeProperty().addListener(colorSchemeListener);

        syncThemeToggleFromColorScheme(scene.getPreferences().getColorScheme());
    }

    private void syncThemeToggleFromColorScheme(ColorScheme colorScheme) {
        boolean darkMode = colorScheme == ColorScheme.DARK;

        updatingThemeToggle = true;
        try {
            darkModeToggle.setSelected(darkMode);
        } finally {
            updatingThemeToggle = false;
        }

        updateThemeIcon(darkMode);
    }

    private void applyThemeFromToggle(boolean darkModeEnabled) {
        Scene scene = boundScene != null ? boundScene : getScene();
        if (scene == null) {
            return;
        }

        scene.getPreferences().setColorScheme(darkModeEnabled ? ColorScheme.DARK : ColorScheme.LIGHT);
    }

    private void updateThemeIcon(boolean darkModeEnabled) {
        if (themeIcon == null) {
            return;
        }

        themeIcon.setIconLiteral(darkModeEnabled ? "fas-sun" : "far-moon");
    }
}
