package com.bayoumi.controllers.azkar.timed;

import com.bayoumi.models.azkar.TimedZekrDTO;
import com.bayoumi.models.settings.Language;
import com.bayoumi.models.settings.LanguageBundle;
import com.bayoumi.models.settings.Settings;
import com.bayoumi.services.statistics.StatisticsService;
import com.bayoumi.storage.statistics.StatisticsType;
import com.bayoumi.util.Constants;
import com.bayoumi.util.LoggerWrapper;
import com.bayoumi.util.Utility;
import com.bayoumi.util.gui.BuilderUI;
import com.bayoumi.util.gui.PopOverUtil;
import com.bayoumi.util.gui.ScrollHandler;
import com.bayoumi.util.gui.load.Locations;
import com.bayoumi.util.web.FileDownloader;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialog;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TimedAzkarController implements Initializable {
    private MediaPlayer mediaPlayer;
    private ResourceBundle bundle;
    private List<TimedZekrDTO> timedAzkarList;
    private Image morningImage, nightImage;
    private FontIcon pauseIcon, playIcon;
    private int currentIndex = 0;

    private static final Logger LOGGER = LoggerWrapper.loggerFactory(TimedAzkarController.class);

    // =============== FXML Elements ===============
    @FXML
    private StackPane sp;
    @FXML
    private ScrollPane scrollPane;
    @FXML
    private VBox scrollPaneChild, progressBox;
    @FXML
    private TextFlow transliterationTextBox, translationTextBox;
    @FXML
    private Text content, transliteration, translation, fadl;
    @FXML
    private Label title, count, countDescription, paginationText;
    @FXML
    private ImageView image;
    @FXML
    private JFXButton settingsButton, playButton, copyButton, toggleZekrDescriptionButton, previousButton, nextButton;
    @FXML
    private Separator separator;
    @FXML
    private ProgressBar progress;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        progressBox.setVisible(false);

        morningImage = new Image("/com/bayoumi/images/sun_50px.png");
        nightImage = new Image("/com/bayoumi/images/night_50px.png");

        playIcon = new FontIcon(FontAwesomeSolid.PLAY);
        playIcon.setStyle("-fx-fill: -fx-reverse-secondary;");
        playIcon.setIconSize(30);

        pauseIcon = new FontIcon(FontAwesomeSolid.PAUSE);
        pauseIcon.setIconSize(30);
        pauseIcon.setStyle("-fx-fill: -fx-reverse-secondary;");

        settingsButton.setOnMouseEntered(event -> settingsButton.setContentDisplay(ContentDisplay.LEFT));
        settingsButton.setOnMouseExited(event -> settingsButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY));
        ScrollHandler.init(scrollPaneChild, scrollPane, 1);
        updateBundle(LanguageBundle.getInstance().getResourceBundle());

        scrollPaneChild.setOnMouseClicked(event -> onIncreaseCountClicked());

        PopOverUtil.init(copyButton, bundle.getString("timedAzkarCopyTooltip"));
    }

    private void updateBundle(ResourceBundle bundle) {
        this.bundle = bundle;
        settingsButton.setText(bundle.getString("settings"));
        copyButton.setText(bundle.getString("copy"));
        playButton.setText(bundle.getString("playAudio"));
        toggleZekrDescriptionButton.setText(bundle.getString("toggleZekrDescription"));
    }


    public void setData(List<TimedZekrDTO> timedZekrDTOList, String type, Stage stage) {
        currentIndex = 0;
        if (type.toLowerCase().contains("morning")) {
            title.setText(bundle.getString("morningAzkar"));
            image.setImage(morningImage);
            initAzkarContainer(timedZekrDTOList);
        } else {
            title.setText(bundle.getString("nightAzkar"));
            image.setImage(nightImage);
            initAzkarContainer(timedZekrDTOList);
        }

        count.requestFocus();
        stage.setOnCloseRequest(event -> stopIfPlaying());
    }

    @FXML
    private void onPreviousClicked() {
        final boolean isLTR = bundle.getString("dir").equals("LEFT_TO_RIGHT");
        if (isLTR) {
            goToNext();
        } else {
            goToPrevious();
        }
    }

    private void goToPrevious() {
        if (currentIndex > 0) {
            currentIndex--;
            setValuesForCurrentIndex();
        }
    }

    @FXML
    private void onNextClicked() {
        final boolean isLTR = bundle.getString("dir").equals("LEFT_TO_RIGHT");
        if (isLTR) {
            goToPrevious();
        } else {
            goToNext();
        }
    }

    private void goToNext() {
        if (currentIndex < timedAzkarList.size() - 1) {
            currentIndex++;
            setValuesForCurrentIndex();
        }
    }

    @FXML
    private void onIncreaseCountClicked() {
        final int zekrCount = timedAzkarList.get(currentIndex).getCount();
        final int currentCount = Integer.parseInt(count.getText());
        if (currentCount < zekrCount - 1 || (currentIndex == timedAzkarList.size() - 1 && currentCount < zekrCount)) {
            count.setText(String.valueOf(currentCount + 1));
            progress.setProgress((double) (currentCount + 1) / zekrCount);
        } else {
            goToNext();
        }
    }


    private void setValuesForCurrentIndex() {
        TimedZekrDTO zekrDTO = timedAzkarList.get(currentIndex);
        content.setText(zekrDTO.getContent());
        transliteration.setText(zekrDTO.getTransliteration());
        translation.setText(zekrDTO.getTranslation());
        fadl.setText(zekrDTO.getFadl());
        separator.setVisible(!fadl.getText().isEmpty());
        count.setText("0");
        progress.setProgress(0);
        countDescription.setText(zekrDTO.getCountDescription());
        paginationText.setText((currentIndex + 1) + " " + bundle.getString("of") + " " + timedAzkarList.size());
        final boolean isLTR = bundle.getString("dir").equals("LEFT_TO_RIGHT");
        if (isLTR) {
            previousButton.setDisable(currentIndex == timedAzkarList.size() - 1);
            countDescription.setCursor(previousButton.isDisable() ? Cursor.DEFAULT : Cursor.HAND);

            nextButton.setDisable(currentIndex == 0);
            paginationText.setCursor(nextButton.isDisable() ? Cursor.DEFAULT : Cursor.HAND);
        } else {
            previousButton.setDisable(currentIndex == 0);
            countDescription.setCursor(previousButton.isDisable() ? Cursor.DEFAULT : Cursor.HAND);

            nextButton.setDisable(currentIndex == timedAzkarList.size() - 1);
            paginationText.setCursor(nextButton.isDisable() ? Cursor.DEFAULT : Cursor.HAND);
        }


        playButton.setDisable(zekrDTO.getAudio().isEmpty());

        if (isMediaPlaying()) {
            pauseOrStopMedia(null);
        }
    }


    private void initAzkarContainer(List<TimedZekrDTO> timedZekrDTOList) {
        // start with the default font size
        updateFontSize();
        reset();
        try {
            final Language language = Settings.getInstance().getLanguage();
            if (language.equals(Language.Arabic)) {
                scrollPaneChild.getChildren().remove(transliterationTextBox);
                scrollPaneChild.getChildren().remove(translationTextBox);
                transliterationTextBox = null;
                translationTextBox = null;
            } else {
                if (!scrollPaneChild.getChildren().contains(transliterationTextBox)) {
                    scrollPaneChild.getChildren().add(0, transliterationTextBox);
                }
                if (!scrollPaneChild.getChildren().contains(translationTextBox)) {
                    scrollPaneChild.getChildren().add(1, translationTextBox);
                }
            }
            timedAzkarList = timedZekrDTOList;
            if (timedAzkarList.isEmpty()) {
                return;
            }
            setValuesForCurrentIndex();
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Init azkar container failed", ex);
        }
    }

    private void reset() {
        content.setText(null);
        transliteration.setText(null);
        translation.setText(null);
        fadl.setText(null);
        count.setText(null);
        countDescription.setText(null);
        paginationText.setText(null);
        progress.setProgress(0);
    }


    @FXML
    private void onCopyClicked() {
        Utility.copyToClipboard(content.getText());
    }

    @FXML
    private void onToggleZekrDescription() {
        try {
            final FXMLLoader loader = new FXMLLoader(getClass().getResource(Locations.Zekr_Description.toString()));
            final Region view = loader.load();
            // make width and height of the dialog to be 80% of the stage
            view.setPrefWidth(sp.getWidth() * 0.8);
            view.setMaxWidth(800);
            view.setPrefHeight(sp.getHeight() * 0.8);
            view.setMaxHeight(600);
            final JFXDialog dialog = new JFXDialog(sp, view, JFXDialog.DialogTransition.CENTER);
            final TimedZekrDTO zekrDTO = timedAzkarList.get(currentIndex);
            ((ZekrDescriptionController) loader.getController()).setData(dialog, zekrDTO.getHadithText(), zekrDTO.getExplanationOfHadithVocabulary());
            dialog.show();
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Exception in onToggleZekrDescription", ex);
        }
    }

    @FXML
    private void openSettings() {
        try {
            StatisticsService.getInstance().increment(StatisticsType.SETTINGS_TIMED_AZKAR_OPENED);
            final FXMLLoader loader = new FXMLLoader(getClass().getResource(Locations.TimedAzkar_Settings.toString()));
            final JFXDialog dialog = new JFXDialog(sp, loader.load(), JFXDialog.DialogTransition.TOP);
            ((SettingsController) loader.getController()).setData(dialog, this::updateFontSize);
            dialog.show();
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Exception in openSettings", ex);
        }
    }

    private void updateFontSize() {
        this.content.setStyle("-fx-font-family: 'Noto Naskh Arabic'; -fx-font-weight: BOLD; -fx-text-alignment: center;-fx-font-size: " + Settings.getInstance().getAzkarSettings().getTimedAzkarFontSize() + ";");
        this.transliteration.setStyle("-fx-font-family: 'Noto Naskh Arabic'; -fx-font-weight: BOLD; -fx-text-alignment: center;-fx-font-size: " + Settings.getInstance().getAzkarSettings().getTimedAzkarFontSize() + ";");
        this.translation.setStyle("-fx-font-family: 'Noto Naskh Arabic'; -fx-font-weight: BOLD; -fx-text-alignment: center;-fx-font-size: " + Settings.getInstance().getAzkarSettings().getTimedAzkarFontSize() + ";");
        this.fadl.setStyle("-fx-font-family: 'Noto Naskh Arabic'; -fx-font-weight: BOLD; -fx-text-alignment: center;-fx-font-size: " + (Settings.getInstance().getAzkarSettings().getTimedAzkarFontSize() - 6) + ";");
    }

    @FXML
    private void keyEventAction(KeyEvent keyEvent) {
        final KeyCode key = keyEvent.getCode();
        final boolean isLTR = bundle.getString("dir").equals("LEFT_TO_RIGHT");
        final boolean isNextKey = isLTR ? key.equals(KeyCode.RIGHT) : key.equals(KeyCode.LEFT);
        final boolean isPreviousKey = isLTR ? key.equals(KeyCode.LEFT) : key.equals(KeyCode.RIGHT);
        final boolean isIncreaseCountKey = key.equals(KeyCode.UP) || key.equals(KeyCode.SPACE);

        if (isNextKey) {
            goToNext();
        } else if (isPreviousKey) {
            goToPrevious();
        } else if (isIncreaseCountKey) {
            onIncreaseCountClicked();
        }
    }

    @FXML
    private void onPlayClicked() {
        if (isMediaPlaying()) {
            pauseOrStopMedia(null);
        } else {
            final String audioPath = timedAzkarList.get(currentIndex).getAudio();
            LOGGER.info(() -> "Audio path: " + audioPath);
            final File audioFile = new File(System.getProperty("java.io.tmpdir") + "/" + Constants.APP_NAME + "/" + "downloaded_audio.mp3");
            if (audioFile.delete()) {
                LOGGER.info(() -> "File deleted successfully");
            }
            progressBox.setVisible(true);
            new Thread(() -> {
                if (FileDownloader.downloadFile(audioPath, audioFile)) {
                    Platform.runLater(() -> playMedia(audioFile));
                } else {
                    Platform.runLater(() -> BuilderUI.showOkAlert(Alert.AlertType.ERROR, bundle.getString("errorDownloadingAudio"), bundle));
                }
                Platform.runLater(() -> progressBox.setVisible(false));
            }).start();
        }
    }

    private void pauseOrStopMedia(File audioFile) {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        if (audioFile != null && audioFile.exists() && !audioFile.delete()) {
            LOGGER.severe(() -> "Failed to delete audio file.");
        }
        playButton.setGraphic(playIcon);
        playButton.setPadding(new Insets(5, 11, 5, 11));
        playButton.setText(bundle.getString("playAudio"));
    }

    private void playMedia(File audioFile) {
        try {
            mediaPlayer = new MediaPlayer(new Media(audioFile.toURI().toString()));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error playing audio", e);
            BuilderUI.showOkAlert(Alert.AlertType.ERROR, bundle.getString("errorPlayingAudio"), bundle);
            return;
        }
        mediaPlayer.setVolume(100);
        mediaPlayer.play();
        mediaPlayer.setOnReady(() -> LOGGER.info(() -> "Media is ready."));
        mediaPlayer.setOnPlaying(() -> {
            LOGGER.info(() -> "Media is playing.");
            if (isWindowClosed()) {
                pauseOrStopMedia(audioFile);
            }
        });
        mediaPlayer.setOnPaused(() -> pauseOrStopMedia(audioFile));
        mediaPlayer.setOnStopped(() -> pauseOrStopMedia(audioFile));
        mediaPlayer.setOnEndOfMedia(() -> pauseOrStopMedia(audioFile));

        playButton.setGraphic(pauseIcon);
        playButton.setPadding(new Insets(5, 11, 5, 11));
        playButton.setText(bundle.getString("stopAudio"));
    }

    private void stopIfPlaying() {
        if (isMediaPlaying()) {
            mediaPlayer.stop();
        }
    }

    private boolean isMediaPlaying() {
        return mediaPlayer != null && mediaPlayer.getStatus().equals(MediaPlayer.Status.PLAYING);
    }

    private boolean isWindowClosed() {
        return !progressBox.getScene().getWindow().isShowing();
    }
}
