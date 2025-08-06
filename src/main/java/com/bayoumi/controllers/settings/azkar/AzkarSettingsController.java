package com.bayoumi.controllers.settings.azkar;

import com.bayoumi.controllers.settings.SettingsInterface;
import com.bayoumi.models.azkar.AbsoluteZekr;
import com.bayoumi.models.settings.*;
import com.bayoumi.services.statistics.StatisticsService;
import com.bayoumi.storage.statistics.StatisticsType;
import com.bayoumi.util.LoggerWrapper;
import com.bayoumi.util.gui.*;
import com.bayoumi.util.gui.load.Loader;
import com.bayoumi.util.gui.load.LoaderComponent;
import com.bayoumi.util.gui.load.Locations;
import com.bayoumi.util.gui.notfication.NotificationAudio;
import com.bayoumi.util.time.ArabicNumeralDiscrimination;
import com.jfoenix.controls.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.controlsfx.control.Notifications;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AzkarSettingsController implements Initializable, SettingsInterface {

    private ResourceBundle bundle;
    private FontIcon pauseIcon;
    private FontIcon playIcon;
    private AzkarSettings azkarSettings;
    private NotificationSettings notificationSettings;
    private JFXButton currentFrequency;
    private MediaPlayer MEDIA_PLAYER;
    private double previousValue = 50;
    private boolean isMuted = false;

    private static final Logger LOGGER = LoggerWrapper.loggerFactory(AzkarSettingsController.class);


    @FXML
    private ScrollPane scrollPane;
    @FXML
    private VBox root, periodBox;
    @FXML
    private JFXComboBox<Pos> posComboBox;
    @FXML
    private JFXButton highFrequency, midFrequency, lowFrequency, rearFrequency;
    @FXML
    private Spinner<Integer> azkarPeriod, azkarPeriod_hour, morningAzkarTimeSpinner, nightAzkarTimeSpinner, azkarDuration;
    @FXML
    private JFXCheckBox stopAzkar;
    @FXML
    private JFXComboBox<String> azkarAlarmComboBox;
    @FXML
    private JFXButton playButton, showZekrButton, goToAzkarDBButton, notificationColorButton;
    @FXML
    private FontIcon volume;
    @FXML
    private JFXSlider volumeSlider;
    @FXML
    private HBox volumeBox, azkarDurationBox;
    @FXML
    private JFXToggleButton morningAzkarTimeToggle, nightAzkarTimeToggle;
    @FXML
    private Label minPlurality, hourPlurality, choosePeriod, zakrAppearEvery, theSoundAndLocationOfTheAlertForAzkar,
            morningAndNightAzkarTitle, reminderOfMorningAzkarLabel, afterFajrPrayerLabel, reminderOfNightAzkarLabel,
            afterAsrPrayerLabel, minuteLabelForMorningAzkar, minuteLabelForNightAzkar, secondPlurality, zekrDurationEvery;


    public void updateBundle(ResourceBundle bundle) {
        this.bundle = bundle;
        hourPlurality.setText(bundle.getString("oneHour"));
        minPlurality.setText(bundle.getString("oneMinute"));
        secondPlurality.setText(bundle.getString("seconds"));
        choosePeriod.setText(bundle.getString("settings.azkar.choosePeriod"));
        highFrequency.setText(bundle.getString("azkar.period.high"));
        midFrequency.setText(bundle.getString("azkar.period.mid"));
        lowFrequency.setText(bundle.getString("azkar.period.low"));
        rearFrequency.setText(bundle.getString("azkar.period.rare"));
        zakrAppearEvery.setText(bundle.getString("settings.azkar.zakrAppearEvery") + ":");
        zekrDurationEvery.setText(bundle.getString("settings.azkar.zekrDurationEvery") + ":");
        stopAzkar.setText(bundle.getString("settings.azkar.stopTheAutomaticAppearanceOfAzkar"));
        goToAzkarDBButton.setText(bundle.getString("settings.azkar.azkarDatabase"));
        showZekrButton.setText(bundle.getString("settings.azkar.showZekr"));
        notificationColorButton.setText(bundle.getString("settings.azkar.notificationColor"));
        posComboBox.setPromptText(bundle.getString("settings.azkar.notificationLocation"));
        azkarAlarmComboBox.setPromptText(bundle.getString("settings.azkar.alarmSound"));
        theSoundAndLocationOfTheAlertForAzkar.setText(bundle.getString("settings.azkar.theSoundAndLocationOfTheAlertForAzkar"));
        morningAndNightAzkarTitle.setText(bundle.getString("settings.azkar.morningAndNightAzkar"));
        reminderOfMorningAzkarLabel.setText(bundle.getString("settings.azkar.reminderOfMorningAzkar"));
        reminderOfNightAzkarLabel.setText(bundle.getString("settings.azkar.reminderOfNightAzkar"));
        afterFajrPrayerLabel.setText(bundle.getString("settings.azkar.afterFajrPrayer"));
        afterAsrPrayerLabel.setText(bundle.getString("settings.azkar.afterAsrPrayer"));
        minuteLabelForMorningAzkar.setText(bundle.getString("oneMinute"));
        minuteLabelForNightAzkar.setText(bundle.getString("oneMinute"));

        PopOverUtil.init(goToAzkarDBButton, (bundle.getString("settings.azkar.azkarDatabaseButtonNote")));
    }


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        updateBundle(LanguageBundle.getInstance().getResourceBundle());

        playIcon = new FontIcon(FontAwesomeSolid.PLAY);
        playIcon.setStyle("-fx-fill: -fx-reverse-secondary;");
        playIcon.setIconSize(30);

        pauseIcon = new FontIcon(FontAwesomeSolid.PAUSE);
        pauseIcon.setIconSize(30);
        pauseIcon.setStyle("-fx-fill: -fx-reverse-secondary;");

        periodBox.disableProperty().bind(stopAzkar.selectedProperty());
        azkarDurationBox.disableProperty().bind(stopAzkar.selectedProperty());
        // init Spinner Values
        IntegerSpinner.init(azkarPeriod);
        IntegerSpinner.init(azkarPeriod_hour);
        azkarPeriod.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 1));
        azkarPeriod_hour.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 0));
        azkarDuration.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 60, 10, 5));
        azkarPeriod.valueProperty().addListener((observable, oldValue, newValue) -> minPlurality.setText(ArabicNumeralDiscrimination.minutesArabicPlurality(bundle, Integer.parseInt(azkarPeriod.getEditor().getText()))));
        azkarPeriod_hour.valueProperty().addListener((observable, oldValue, newValue) -> hourPlurality.setText(ArabicNumeralDiscrimination.hoursArabicPlurality(bundle, Integer.parseInt(azkarPeriod_hour.getEditor().getText()))));
        azkarPeriod.setOnKeyReleased(event -> minPlurality.setText(ArabicNumeralDiscrimination.minutesArabicPlurality(bundle, Integer.parseInt(azkarPeriod.getEditor().getText()))));
        azkarPeriod_hour.setOnKeyReleased(event -> hourPlurality.setText(ArabicNumeralDiscrimination.hoursArabicPlurality(bundle, Integer.parseInt(azkarPeriod_hour.getEditor().getText()))));
        morningAzkarTimeSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 120, 30, 10));
        nightAzkarTimeSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 120, 30, 10));


        // init Saved data form DB
        azkarSettings = Settings.getInstance().getAzkarSettings();
        azkarAlarmComboBox.setValue(azkarSettings.getAudioName());
        List<String> notificationAudioFileNames = NotificationAudio.getAudios()
                .stream()
                .map(NotificationAudio::getFileName)
                .toList();
        azkarAlarmComboBox.setItems(FXCollections.observableList(notificationAudioFileNames));
        playButton.setDisable(azkarAlarmComboBox.getValue().equals("بدون صوت"));
        azkarAlarmComboBox.setOnAction(event -> {
            playButton.setDisable(azkarAlarmComboBox.getValue().equals("بدون صوت"));
            volumeBox.setDisable(azkarAlarmComboBox.getValue().equals("بدون صوت"));
            if (MEDIA_PLAYER != null && MEDIA_PLAYER.getStatus().equals(MediaPlayer.Status.PLAYING)) {
                MEDIA_PLAYER.stop();
                MEDIA_PLAYER.dispose(); // Release the resources
                MEDIA_PLAYER = null;   // Remove reference
                playButton.setGraphic(playIcon);
                playButton.setPadding(new Insets(5, 14, 5, 8));
            }
            onAzkarAlarmChange();
        });
        volumeSlider.setValue(azkarSettings.getVolume());
        // init volume
        volumeBox.setDisable(azkarAlarmComboBox.getValue().equals("بدون صوت"));
        // volume config
        volumeSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            azkarSettings.setVolume((int) volumeSlider.getValue());
            playButton.requestFocus();
            previousValue = (double) oldValue;
            // multiply duration by percentage calculated by
            // slider position
            if (volumeSlider.getValue() > 0) {
                volume.setIconCode(FontAwesomeSolid.VOLUME_UP);
            } else if (volumeSlider.getValue() == 0) {
                volume.setIconCode(FontAwesomeSolid.VOLUME_MUTE);
            }
            if (null != MEDIA_PLAYER) {
                MEDIA_PLAYER.setVolume(azkarSettings.getVolume() / 100.0);
            }
        });

        notificationSettings = Settings.getInstance().getNotificationSettings();
        posComboBox.setItems(FXCollections.observableArrayList(Pos.TOP_RIGHT, Pos.BOTTOM_RIGHT, Pos.TOP_LEFT, Pos.BOTTOM_LEFT, Pos.CENTER));
        posComboBox.setValue(notificationSettings.getPosition());
        if (Settings.getInstance().getLanguage().equals(Language.Arabic)) {
            posComboBox.setConverter(NotificationSettings.posArabicConverter());
        } else {
            posComboBox.setConverter(NotificationSettings.posEnglishConverter());
        }

        azkarPeriod.getValueFactory().setValue(azkarSettings.getHighPeriod() % 60);
        azkarPeriod_hour.getValueFactory().setValue(azkarSettings.getHighPeriod() / 60);
        azkarDuration.getValueFactory().setValue(azkarSettings.getAzkarDuration());

        morningAzkarTimeSpinner.getValueFactory().setValue(Settings.getInstance().getAzkarSettings().getMorningAzkarReminder());
        nightAzkarTimeSpinner.getValueFactory().setValue(Settings.getInstance().getAzkarSettings().getNightAzkarReminder());

        morningAzkarTimeToggle.setSelected(morningAzkarTimeSpinner.getValueFactory().getValue() != 0);
        morningAzkarTimeSpinner.setDisable(morningAzkarTimeSpinner.getValueFactory().getValue() == 0);
        toggleAction(morningAzkarTimeToggle);

        nightAzkarTimeToggle.setSelected(nightAzkarTimeSpinner.getValueFactory().getValue() != 0);
        nightAzkarTimeSpinner.setDisable(nightAzkarTimeSpinner.getValueFactory().getValue() == 0);
        toggleAction(nightAzkarTimeToggle);

        stopAzkar.setSelected(azkarSettings.isStopped());
        currentFrequency = highFrequency;
        currentFrequency.getStyleClass().add("frequency-btn-selected");

        ScrollHandler.init(root, scrollPane, 3);
    }

    @FXML
    private void play() {
        if (MEDIA_PLAYER != null && MEDIA_PLAYER.getStatus().equals(MediaPlayer.Status.PLAYING)) {
            MEDIA_PLAYER.stop();
            MEDIA_PLAYER.dispose(); // Release the resources
            MEDIA_PLAYER = null;   // Remove reference
            playButton.setGraphic(playIcon);
        } else {
            String fileName = azkarAlarmComboBox.getValue();
            LOGGER.info(() -> fileName);
            if (!fileName.equals("بدون صوت")) {
                try {
                    // TODO: There's a bug here where the file name is not what is expected
                    //  but and it should be replaced with the path to the audio
                    MEDIA_PLAYER = new MediaPlayer(new Media(fileName));
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Play failed", e);
                    BuilderUI.showOkAlert(Alert.AlertType.ERROR, bundle.getString("errorPlayingAudio"), bundle);
                    return;
                }
                MEDIA_PLAYER.setVolume(azkarSettings.getVolume() / 100.0);
                MEDIA_PLAYER.play();
                // playing
                playButton.setGraphic(pauseIcon);
                playButton.setPadding(new Insets(5, 11, 5, 11));
                MEDIA_PLAYER.setOnEndOfMedia(() -> playButton.setGraphic(playIcon));
            }
        }
    }

    @FXML
    private void highFrequencyAction() {
        toggleFrequencyBTN(highFrequency);
        azkarPeriod.getValueFactory().setValue(azkarSettings.getHighPeriod() % 60);
        azkarPeriod_hour.getValueFactory().setValue(azkarSettings.getHighPeriod() / 60);
    }

    @FXML
    private void lowFrequencyAction() {
        toggleFrequencyBTN(lowFrequency);
        azkarPeriod.getValueFactory().setValue(azkarSettings.getLowPeriod() % 60);
        azkarPeriod_hour.getValueFactory().setValue(azkarSettings.getLowPeriod() / 60);
    }

    @FXML
    private void midFrequencyAction() {
        toggleFrequencyBTN(midFrequency);
        azkarPeriod.getValueFactory().setValue(azkarSettings.getMidPeriod() % 60);
        azkarPeriod_hour.getValueFactory().setValue(azkarSettings.getMidPeriod() / 60);
    }

    @FXML
    private void rearFrequencyAction() {
        toggleFrequencyBTN(rearFrequency);
        azkarPeriod.getValueFactory().setValue(azkarSettings.getRearPeriod() % 60);
        azkarPeriod_hour.getValueFactory().setValue(azkarSettings.getRearPeriod() / 60);
    }

    private void toggleFrequencyBTN(JFXButton b) {
        azkarPeriod.getValueFactory().setValue(Integer.valueOf(azkarPeriod.getEditor().getText()));
        azkarPeriod_hour.getValueFactory().setValue(Integer.valueOf(azkarPeriod_hour.getEditor().getText()));
        if (azkarPeriod.getEditor().getText().equals("0") && azkarPeriod_hour.getEditor().getText().equals("0")) {
            azkarPeriod.getValueFactory().setValue(1);
        }
        // save data
        saveCurrentFrequency();
        // toggle style to selected button
        currentFrequency.getStyleClass().remove("frequency-btn-selected");
        currentFrequency = b;
        currentFrequency.getStyleClass().add("frequency-btn-selected");
    }

    private void saveCurrentFrequency() {
        int currentPeriodValue = azkarPeriod.getValueFactory().getValue() + azkarPeriod_hour.getValueFactory().getValue() * 60;
        if (currentFrequency.equals(highFrequency)) {
            if (currentPeriodValue == azkarSettings.getHighPeriod()) return;
            azkarSettings.setHighPeriod(currentPeriodValue);
        } else if (currentFrequency.equals(midFrequency)) {
            if (currentPeriodValue == azkarSettings.getMidPeriod()) return;
            azkarSettings.setMidPeriod(currentPeriodValue);
        } else if (currentFrequency.equals(lowFrequency)) {
            if (currentPeriodValue == azkarSettings.getLowPeriod()) return;
            azkarSettings.setLowPeriod(currentPeriodValue);
        } else if (currentFrequency.equals(rearFrequency)) {
            if (currentPeriodValue == azkarSettings.getRearPeriod()) return;
            azkarSettings.setRearPeriod(currentPeriodValue);
        }
    }


    @FXML
    private void goToNotificationColor() {
        try {
            StatisticsService.getInstance().increment(StatisticsType.SETTINGS_NOTIFICATION_COLORS_OPENED);
            final LoaderComponent popUp = Loader.getInstance().getPopUp(Locations.ChooseNotificationColor);
            ((ChooseNotificationColorController) popUp.getController()).setData();
            popUp.showAndWait();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Go to notification color failed", e);
        }
    }

    @FXML
    private void goToAzkar() {
        try {
            StatisticsService.getInstance().increment(StatisticsType.SETTINGS_AZKAR_DB_OPENED);
            final Stage stage = new Stage();
            final Scene scene = new Scene(FXMLLoader.load(Objects.requireNonNull(getClass().getResource(Locations.AbsoluteAzkar.toString()))));
            scene.getStylesheets().setAll(Settings.getInstance().getThemeFilesCSS());
            stage.setScene(scene);
            HelperMethods.SetIcon(stage);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Go to azkar failed", e);
        }
    }

    @Override
    public void saveToDB() {
        try {
            highFrequency.fire();
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Save to DB failed", ex);
        }
    }

    @FXML
    private void selectPosition() {
        notificationSettings.setPosition(posComboBox.getValue());
    }

    @FXML
    private void onStopAzkarChange() {
        azkarSettings.setStopped(stopAzkar.isSelected());
    }

    private void onAzkarAlarmChange() {
        azkarSettings.setAudioName(azkarAlarmComboBox.getValue());
    }


    @FXML
    private void showZekr() {
        if (AbsoluteZekr.absoluteZekrObservableList.isEmpty()) {
            return;
        }
        Platform.runLater(()
                -> {
            Image image = null;
            if (new Random().nextInt(999) % 2 == 0) {
                image = new Image("/com/bayoumi/images/Kaaba.png");
            }
            try {
                // TODO: Fix add the notification sounds back
                int index = new Random().nextInt(AbsoluteZekr.absoluteZekrObservableList.size());
                String text = AbsoluteZekr.absoluteZekrObservableList.get(index).getText();
                Notifications.create()
                        .title(text)
                        .text(text)
                        .graphic(new ImageView(new Image("/com/bayoumi/images/Kaaba.png")))
                        // TODO: Fix maybe the duration type here is incorrect
                        .hideAfter(Duration.millis(azkarSettings.getAzkarDuration()))
                        .position(notificationSettings.getPosition())
                        .show();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Show zekr failed", ex);
            }
        });
    }

    @FXML
    private void muteUnmute() {
        if (isMuted) {
            // umMute
            isMuted = false;
            volumeSlider.setValue(previousValue);
        } else {
            // mute
            isMuted = true;
            previousValue = volumeSlider.getValue();
            volumeSlider.setValue(0);
        }
    }


    @FXML
    private void onMorningAzkarTimeChange() {
        azkarSettings.setMorningAzkarReminder(morningAzkarTimeSpinner.getValueFactory().getValue());
        if (morningAzkarTimeSpinner.getValueFactory().getValue() == 0) {
            morningAzkarTimeToggle.setSelected(false);
            morningAzkarTimeSpinner.setDisable(true);
            toggleAction(morningAzkarTimeToggle);
        }
    }

    @FXML
    private void onNightAzkarTimeChange() {
        azkarSettings.setNightAzkarReminder(nightAzkarTimeSpinner.getValueFactory().getValue());
        if (nightAzkarTimeSpinner.getValueFactory().getValue() == 0) {
            nightAzkarTimeToggle.setSelected(false);
            nightAzkarTimeSpinner.setDisable(true);
            toggleAction(nightAzkarTimeToggle);
        }
    }

    @FXML
    private void onMorningAzkarTimeToggle() {
        toggleAction(morningAzkarTimeToggle);
        if (morningAzkarTimeToggle.isSelected()) {
            morningAzkarTimeSpinner.setDisable(false);
            morningAzkarTimeSpinner.getValueFactory().setValue(30);
            azkarSettings.setMorningAzkarReminder(30);
        } else {
            morningAzkarTimeSpinner.setDisable(true);
            morningAzkarTimeSpinner.getValueFactory().setValue(0);
            azkarSettings.setMorningAzkarReminder(0);
        }
    }

    @FXML
    private void onNightAzkarTimeToggle() {
        toggleAction(nightAzkarTimeToggle);
        if (nightAzkarTimeToggle.isSelected()) {
            nightAzkarTimeSpinner.setDisable(false);
            nightAzkarTimeSpinner.getValueFactory().setValue(30);
            azkarSettings.setNightAzkarReminder(30);
        } else {
            nightAzkarTimeSpinner.setDisable(true);
            nightAzkarTimeSpinner.getValueFactory().setValue(0);
            azkarSettings.setNightAzkarReminder(0);
        }
    }

    private void toggleAction(JFXToggleButton toggleButton) {
        if (toggleButton.isSelected()) {
            toggleButton.setText(bundle.getString("enabled"));
        } else {
            toggleButton.setText(bundle.getString("settings.azkar.noReminder"));
        }
    }

    @FXML
    private void onAzkarDurationChange() {
        secondPlurality.setText(ArabicNumeralDiscrimination.secondsArabicPlurality(bundle));
        azkarSettings.setAzkarDuration(azkarDuration.getValueFactory().getValue());
    }

}
