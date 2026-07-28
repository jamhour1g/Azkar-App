package com.azkar.components;

import com.azkar.i18n.AppLocale;
import javafx.application.ColorScheme;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import lombok.val;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;

public class MainScreenHeader extends HBox {

    private enum HeaderTab {
        HOME,
        LIBRARY
    }


    private final ObjectProperty<EventHandler<ActionEvent>> onHomeAction = new SimpleObjectProperty<>(this, "onHomeAction");

    private final ObjectProperty<EventHandler<ActionEvent>> onAzkarLibraryAction = new SimpleObjectProperty<>(this, "onAzkarLibraryAction");

    private final ObjectProperty<EventHandler<ActionEvent>> onSettingsAction = new SimpleObjectProperty<>(this, "onSettingsAction");

    @FXML
    private Button homeHeaderBtn;

    @FXML
    private Button azkarLibHeaderBtn;

    @FXML
    private Button settingsBtn;

    @FXML
    private ToggleButton darkModeToggle;

    @FXML
    private FontIcon themeIcon;

    private final ChangeListener<ColorScheme> colorSchemeListener = (_, _, newScheme) -> {
        boolean darkMode = (newScheme == ColorScheme.DARK);
        darkModeToggle.setSelected(darkMode);
        updateThemeIcon(darkMode);
    };

    private static final String FXML_PATH = "/com/azkar/components/home/main_screen_header.fxml";

    public MainScreenHeader() {
        super();
        val fxmlLoader = new FXMLLoader(
                getClass().getResource(FXML_PATH),
                AppLocale.bundle());
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load FXML", e);
        }

        onHomeAction.addListener((_, _, newVal) -> homeHeaderBtn.setOnAction(e -> {
            setActiveTab(HeaderTab.HOME);
            newVal.handle(e);
        }));

        onAzkarLibraryAction.addListener((_, _, newVal) -> azkarLibHeaderBtn.setOnAction(e -> {
            setActiveTab(HeaderTab.LIBRARY);
            newVal.handle(e);
        }));

        onSettingsAction.addListener((_, _, newVal) -> settingsBtn.setOnAction(newVal));

        setupThemeToggle();
    }

    public final ObjectProperty<EventHandler<ActionEvent>> onHomeActionProperty() {
        return onHomeAction;
    }

    public final ObjectProperty<EventHandler<ActionEvent>> onAzkarLibraryActionProperty() {
        return onAzkarLibraryAction;
    }

    public final ObjectProperty<EventHandler<ActionEvent>> onSettingsActionProperty() {
        return onSettingsAction;
    }

    public final void setOnHomeAction(EventHandler<ActionEvent> value) {
        onHomeActionProperty().set(value);
    }

    public final EventHandler<ActionEvent> getOnHomeAction() {
        return onHomeActionProperty().get();
    }

    public final void setOnAzkarLibraryAction(EventHandler<ActionEvent> value) {
        onAzkarLibraryActionProperty().set(value);
    }

    public final EventHandler<ActionEvent> getOnAzkarLibraryAction() {
        return onAzkarLibraryActionProperty().get();
    }

    public final void setOnSettingsAction(EventHandler<ActionEvent> value) {
        onSettingsActionProperty().set(value);
    }

    public final EventHandler<ActionEvent> getOnSettingsAction() {
        return onSettingsActionProperty().get();
    }

    private void setActiveTab(HeaderTab tab) {
        homeHeaderBtn.getStyleClass().remove("nav-btn-active");
        azkarLibHeaderBtn.getStyleClass().remove("nav-btn-active");
        switch (tab) {
            case HOME -> homeHeaderBtn.getStyleClass().add("nav-btn-active");
            case LIBRARY -> azkarLibHeaderBtn.getStyleClass().add("nav-btn-active");
        }
    }


    private void setupThemeToggle() {
        darkModeToggle.selectedProperty().addListener((_, _, isSelected) -> {
            applyThemeFromToggle(isSelected);
            updateThemeIcon(isSelected);
        });

        sceneProperty().addListener((_, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.getPreferences().colorSchemeProperty().removeListener(colorSchemeListener);
            }

            if (newScene != null) {
                newScene.getPreferences().colorSchemeProperty().addListener(colorSchemeListener);
                syncToggleFromScene(newScene);
            }
        });

        if (getScene() != null) {
            getScene().getPreferences().colorSchemeProperty().addListener(colorSchemeListener);
            syncToggleFromScene(getScene());
        } else {
            updateThemeIcon(darkModeToggle.isSelected());
        }
    }

    private void syncToggleFromScene(Scene scene) {
        boolean darkMode = scene.getPreferences().getColorScheme() == ColorScheme.DARK;
        darkModeToggle.setSelected(darkMode);
        updateThemeIcon(darkMode);
    }

    private void applyThemeFromToggle(boolean darkModeEnabled) {
        getScene().getPreferences().setColorScheme(darkModeEnabled ? ColorScheme.DARK : ColorScheme.LIGHT);
    }

    private void updateThemeIcon(boolean darkModeEnabled) {
        themeIcon.setIconLiteral(darkModeEnabled ? "fas-sun" : "far-moon");
    }

}
