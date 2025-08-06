package com.bayoumi.controllers.settings.other;

import com.bayoumi.Launcher;
import com.bayoumi.controllers.settings.SettingsInterface;
import com.bayoumi.models.settings.Language;
import com.bayoumi.models.settings.LanguageBundle;
import com.bayoumi.models.settings.NotificationColor;
import com.bayoumi.models.settings.Settings;
import com.bayoumi.services.update.UpdateHandler;
import com.bayoumi.util.Constants;
import com.bayoumi.util.LoggerWrapper;
import com.bayoumi.util.gui.BuilderUI;
import com.bayoumi.util.gui.HelperMethods;
import com.bayoumi.util.gui.ScrollHandler;
import com.bayoumi.util.gui.load.Locations;
import com.bayoumi.util.time.HijriDate;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXToggleButton;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.*;
import java.net.URI;
import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OtherSettingsController implements Initializable, SettingsInterface {

    private static final Logger LOGGER = LoggerWrapper.loggerFactory(OtherSettingsController.class);

    private ResourceBundle bundle;
    @FXML
    private ComboBox<Language> languageComboBox;
    @FXML
    private JFXCheckBox autoUpdateCheckBox, usageDataCheckBox;
    @FXML
    private Spinner<Integer> hijriDateOffset;
    @FXML
    private Label minimizeAtStart, format24, darkTheme, hijriDateLabel, version, adjustingTheHijriDateText, languageText, adjustingTheHijriDateNote, usageStatsLabel,
            versionNumberLabel, website, termsOfUse, privacyPolicy, shareLabel;
    @FXML
    private VBox scrollChild, loadingBox;
    @FXML
    private Button checkForUpdateButton, forProblemsAndSuggestionsButton;
    @FXML
    private ScrollPane scrollPane;
    @FXML
    private JFXToggleButton minimizeAtStartToggle, format24Toggle, darkThemeToggle;


    public void updateBundle(ResourceBundle bundle) {
        this.bundle = bundle;
        languageText.setText(bundle.getString("language"));
        format24.setText(bundle.getString("hour24System"));
        darkTheme.setText(bundle.getString("darkTheme"));
        minimizeAtStart.setText(bundle.getString("minimizeAtStart"));
        adjustingTheHijriDateText.setText(bundle.getString("adjustingTheHijriDateText"));
        adjustingTheHijriDateNote.setText(bundle.getString("adjustingTheHijriDateNote"));
        checkForUpdateButton.setText(bundle.getString("checkForUpdate"));
        forProblemsAndSuggestionsButton.setText(bundle.getString("forProblemsAndSuggestions"));
        autoUpdateCheckBox.setText(bundle.getString("checkForUpdatesAutomatically"));
        usageDataCheckBox.setText(bundle.getString("usageDataCheckBox"));
        usageStatsLabel.setText(bundle.getString("usageStats"));
        versionNumberLabel.setText(bundle.getString("versionNumber"));
        website.setText(bundle.getString("website"));
        termsOfUse.setText(bundle.getString("termsOfUse"));
        privacyPolicy.setText(bundle.getString("privacyPolicy"));
        shareLabel.setText(bundle.getString("shareLabel"));

        if (hijriDateOffset.getValue() != null) {
            hijriDateLabel.setText(new HijriDate(hijriDateOffset.getValue()).getString(this.bundle.getLocale().toString()));
        }

        toggleAction(format24Toggle);
        toggleAction(minimizeAtStartToggle);
        toggleAction(darkThemeToggle);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            ScrollHandler.init(scrollChild, scrollPane, 4);
            final Settings settings = Settings.getInstance();


            hijriDateLabel.setText(new HijriDate(settings.getHijriOffset()).getString(settings.getLanguage().getLocale()));
            hijriDateOffset.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(-20, 20, 0));
            hijriDateOffset.getValueFactory().setValue(settings.getHijriOffset());
            hijriDateOffset.valueProperty().addListener((observable, oldValue, newValue) ->
                    hijriDateLabel.setText(new HijriDate(hijriDateOffset.getValue()).getString(settings.getLanguage().getLocale())));


            languageComboBox.setConverter(Language.stringConvertor(languageComboBox));
            languageComboBox.setItems(FXCollections.observableArrayList(Language.values()));
            languageComboBox.setValue(settings.getLanguage());

            format24Toggle.setSelected(settings.getEnable24Format());
            minimizeAtStartToggle.setSelected(settings.getMinimized());
            darkThemeToggle.setSelected(settings.getNightMode());

            version.setText(Constants.VERSION);

            autoUpdateCheckBox.setSelected(settings.getAutomaticCheckForUpdates());
            usageDataCheckBox.setSelected(settings.getSendUsageData());

            updateBundle(LanguageBundle.getInstance().getResourceBundle());
            LanguageBundle.getInstance().addObserver((o, arg) -> updateBundle(LanguageBundle.getInstance().getResourceBundle()));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Initialize failed", e);
        }
    }

    private void openLink(String link, String methodName) {
        try {
            Desktop.getDesktop().browse(new URI(link));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Open link failed", e);
        }
    }

    @FXML
    private void openGitHub() {
        openLink("https://github.com/AbdelrahmanBayoumi/Azkar-App/", "openGtiHub");
    }

    @FXML
    private void openFacebook() {
        openLink("http://fb.com/azkar.application", "openFacebook");
    }

    @FXML
    private void openInstagram() {
        openLink("http://instagram.com/azkar.application", "openInstagram");
    }

    @FXML
    private void openX() {
        openLink("https://x.com/AzkarSoftware", "openGtiHub");
    }

    @FXML
    private void openWebsite() {
        openLink("https://azkar-site.web.app/", "openWebsite");
    }

    @FXML
    private void openUsageDataSite() {
        openLink("https://azkar-site.web.app/desktop/usage-data/", "openUsageDataSite");
        try {
            Desktop.getDesktop().browse(new URI("https://azkar-site.web.app/desktop/usage-data/"));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Open usage data site failed", e);
        }
    }

    @FXML
    private void onTermsOfUseClick() {
        openLink("https://azkar-site.web.app/policies/terms-of-use/", "onTermsOfUseClick");
    }

    @FXML
    private void onPrivacyPolicyClicked() {
        openLink("https://azkar-site.web.app/policies/privacy-policy/", "onPrivacyPolicyClicked");
    }

    @Override
    public void saveToDB() {
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
            LOGGER.log(Level.SEVERE, "Open feedback failed", e);
        }
    }

    @FXML
    private void checkForUpdate() {
        loadingBox.setVisible(true);
        new Thread(() -> {
            switch (UpdateHandler.getInstance().checkUpdate()) {
                case 0:
                    LOGGER.info(() -> "No Update Found");
                    Platform.runLater(() -> BuilderUI.showOkAlert(Alert.AlertType.INFORMATION, this.bundle.getString("thereAreNoNewUpdates"), bundle));
                    break;
                case 1:
                    UpdateHandler.getInstance().showInstallPrompt();
                    break;
                case -1:
                    LOGGER.info(() -> "Only installers and single bundle archives on macOS are supported for background updates");
                    Platform.runLater(() -> BuilderUI.showOkAlert(Alert.AlertType.ERROR, this.bundle.getString("problemInSearchingForUpdates"), bundle));
                    break;
            }
            Platform.runLater(() -> loadingBox.setVisible(false));
        }).start();
    }

    @FXML
    private void saveLanguage() {
        Settings.getInstance().setLanguage(languageComboBox.getValue().getLocale());
    }

    @FXML
    private void autoUpdateCheck() {
        Settings.getInstance().setAutomaticCheckForUpdates(autoUpdateCheckBox.isSelected());
    }

    @FXML
    private void onUsageDataCheck() {
        Settings.getInstance().setSendUsageData(usageDataCheckBox.isSelected());
    }

    @FXML
    private void hijriDateOffsetUpdate() {
        Settings.getInstance().setHijriOffset(hijriDateOffset.getValue());
    }

    @FXML
    private void darkThemeSelect() {
        toggleAction(darkThemeToggle);
        Settings.getInstance().setNightMode(darkThemeToggle.isSelected());
        darkTheme.getScene().getStylesheets().setAll(Settings.getInstance().getThemeFilesCSS());
        Launcher.homeController.changeTheme();
        if (darkThemeToggle.isSelected()) {
            NotificationColor.setDarkTheme();
        } else {
            NotificationColor.setLightTheme();
        }
    }

    @FXML
    private void format24Select() {
        toggleAction(format24Toggle);
        Settings.getInstance().setEnable24Format(format24Toggle.isSelected());
    }

    @FXML
    private void minimizeAtStartSelect() {
        toggleAction(minimizeAtStartToggle);
        Settings.getInstance().setMinimized(minimizeAtStartToggle.isSelected());
    }

    private void toggleAction(JFXToggleButton toggleButton) {
        if (toggleButton.isSelected()) {
            toggleButton.setText(bundle.getString("enabled"));
        } else {
            toggleButton.setText(bundle.getString("disabled"));
        }
    }
}
