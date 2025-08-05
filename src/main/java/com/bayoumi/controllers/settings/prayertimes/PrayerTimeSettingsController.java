package com.bayoumi.controllers.settings.prayertimes;

import com.bayoumi.controllers.components.PrayerCalculationsController;
import com.bayoumi.controllers.components.SelectLocationController;
import com.bayoumi.controllers.components.audio.ChooseAudioController;
import com.bayoumi.controllers.components.audio.ChooseAudioUtil;
import com.bayoumi.controllers.settings.SettingsInterface;
import com.bayoumi.models.settings.LanguageBundle;
import com.bayoumi.models.settings.Settings;
import com.bayoumi.util.LoggerWrapper;
import com.bayoumi.util.Utility;
import com.bayoumi.util.gui.BuilderUI;
import com.bayoumi.util.gui.HelperMethods;
import com.bayoumi.util.gui.ScrollHandler;
import com.bayoumi.util.gui.load.Loader;
import com.bayoumi.util.gui.load.Locations;
import com.jfoenix.controls.JFXCheckBox;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PrayerTimeSettingsController implements Initializable, SettingsInterface {
    @FXML
    private ScrollPane scrollPane;
    @FXML
    private VBox container, adhanContainer;
    @FXML
    private Label adhanLabel;
    @FXML
    private Button forProblemsAndSuggestionsButton;
    @FXML
    private JFXCheckBox stopPrayersReminder;
    private ResourceBundle bundle;

    private static final Logger LOGGER = LoggerWrapper.loggerFactory(PrayerTimeSettingsController.class);


    public void updateBundle(ResourceBundle bundle) {
        this.bundle = bundle;
        adhanLabel.setText(Utility.toUTF(bundle.getString("adhan")));
        forProblemsAndSuggestionsButton.setText(Utility.toUTF(bundle.getString("forProblemsAndSuggestions")));
        stopPrayersReminder.setText(Utility.toUTF(bundle.getString("stopPrayersReminder")));
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        updateBundle(LanguageBundle.getInstance().getResourceBundle());
        ScrollHandler.init(container, scrollPane, 4);
        ChooseAudioUtil.adhan(bundle, adhanContainer);

        stopPrayersReminder.setSelected(Settings.getInstance().getPrayerTimeSettings().isPrayersReminderStopped());

        try {
            container.getChildren().add(container.getChildren().size() - 1, Loader.getInstance().getView(Locations.SelectLocation));
            ((SelectLocationController) Loader.getInstance().getController(Locations.SelectLocation)).setData();

            container.getChildren().add(container.getChildren().size() - 1, Loader.getInstance().getView(Locations.PrayerCalculations));
            ((PrayerCalculationsController) Loader.getInstance().getController(Locations.PrayerCalculations)).setData();
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Initialize failed", ex);
        }
    }

    @FXML
    private void onStopPrayersChange() {
        Settings.getInstance().getPrayerTimeSettings().setPrayersReminderStopped(stopPrayersReminder.isSelected());
    }

    @FXML
    private void openFeedback() {
        try {
            final Scene scene = new Scene(FXMLLoader.load(Objects.requireNonNull(getClass().getResource(Locations.Feedback.toString()))));
            scene.getStylesheets().setAll(Settings.getInstance().getThemeFilesCSS());
            final Stage stage = BuilderUI.initStageDecorated(scene, "");
            HelperMethods.ExitKeyCodeCombination(stage.getScene(), stage);
            stage.showAndWait();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Go to azkar failed", e);
        }
    }

    @Override
    public void saveToDB() {
        try {
            // if auto calc is selected but values is not valid, then => set manual select = true;
            ChooseAudioController.stopIfPlaying();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Save to DB failed", e);
        }
    }

}
