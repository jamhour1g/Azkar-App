package com.azkar.controllers;

import com.azkar.components.home.HomeComponent;
import com.azkar.components.library.LibraryComponent;
import com.azkar.i18n.AppFonts;
import com.azkar.i18n.AppLocale;
import com.azkar.i18n.Keys;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.BorderPane;
import lombok.val;
import org.jspecify.annotations.Nullable;


public class MainScreenController {

    @FXML
    private BorderPane root;

    @FXML
    private HomeComponent homeComponent;

    private @Nullable LibraryComponent libraryComponent;


    @FXML
    public void showLanguageDialog() {
        val currentLocale = AppLocale.current();
        val bundle = AppLocale.bundle();

        val languageDialog = new Alert(Alert.AlertType.CONFIRMATION);
        languageDialog.setTitle(bundle.getString(Keys.Settings.TITLE));
        languageDialog.setHeaderText(bundle.getString(Keys.Settings.LANGUAGE_HEADER));
        languageDialog.setContentText(bundle.getString(Keys.Settings.LANGUAGE_BODY));

        AppFonts.applyFont(languageDialog.getDialogPane(), currentLocale);
        AppLocale.applyNodeOrientation(languageDialog.getDialogPane(), currentLocale);

        val choices = AppLocale.suggestedUiLocales();
        val localeByButton = new LinkedHashMap<ButtonType, Locale>();
        for (val choice : choices) {
            String label = AppLocale.displayLanguage(choice, currentLocale);
            localeByButton.put(new ButtonType(label), choice);
        }

        val cancelButton = ButtonType.CANCEL;
        languageDialog.getButtonTypes().setAll(localeByButton.keySet());
        languageDialog.getButtonTypes().add(cancelButton);

        languageDialog.showAndWait().ifPresent(selection -> {
            if (selection == cancelButton) {
                return;
            }

            Locale chosenLocale = localeByButton.get(selection);
            if (chosenLocale == null) {
                return;
            }

            if (AppLocale.sameLanguage(currentLocale, chosenLocale)) {
                return;
            }

            // Apply the locale and persist it for future sessions.
            // A restart is required for the UI to reflect the new language.
            AppLocale.applyAndPersist(chosenLocale);

            // Inform the user that a restart is required
            Alert restartAlert = new Alert(Alert.AlertType.INFORMATION);
            restartAlert.setTitle("Language Changed");
            restartAlert.setHeaderText("Restart Required");
            restartAlert.setContentText("The language has been saved. Please restart the application to see the changes.");
            AppLocale.applyNodeOrientation(restartAlert.getDialogPane(), chosenLocale);
            restartAlert.showAndWait();
        });
    }

    @FXML
    public void showHome() {
        root.setCenter(homeComponent);
    }

    @FXML
    public void showLibrary() {
        if (libraryComponent == null) {
            libraryComponent = new LibraryComponent();
        }
        root.setCenter(libraryComponent);
    }

    public void shutdown() {
        homeComponent.shutdown();
        if (libraryComponent != null) {
            libraryComponent.shutdown();
        }
    }

}
