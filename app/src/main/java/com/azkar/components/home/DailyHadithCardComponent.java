package com.azkar.components.home;

import com.azkar.data.config.DomainServiceContext;
import com.azkar.domain.model.Remembrance;
import com.azkar.i18n.AppFonts;
import com.azkar.i18n.AppLocale;
import com.azkar.i18n.Keys;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import lombok.val;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Optional;
import java.util.ResourceBundle;

public class DailyHadithCardComponent extends VBox {

    @FXML
    private Text hadithText;

    @FXML
    private Text hadithExplanation;

    @FXML
    private Text hadithSource;

    @FXML
    private Button readMoreButton;

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(DailyHadithCardComponent.class);

    public DailyHadithCardComponent() {
        super();
        var bundle = ResourceBundle.getBundle("com.azkar.i18n.home");
        var fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/home/daily_hadith_card_component.fxml"),
                bundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
            LOGGER.info("Daily hadith card component loaded");
        } catch (IOException e) {
            LOGGER.error("Failed to load daily hadith card component", e);
            throw new IllegalStateException("Failed to load FXML", e);
        }
    }

    @FXML
    public void initialize() {
        readMoreButton.setOnAction(_ -> showHadithDetailsDialog());
        applyDailyHadith();
    }

    public void setHadithText(String text) {
        hadithText.setText(text);
    }

    public void setHadithExplanation(String text) {
        hadithExplanation.setText(text);
    }

    public void setHadithSource(String text) {
        hadithSource.setText(text);
    }

    public String getHadithText() {
        return hadithText.getText();
    }

    public String getHadithExplanation() {
        return hadithExplanation.getText();
    }

    public String getHadithSource() {
        return hadithSource.getText();
    }

    public void showHadithDetailsDialog() {

        val bundle = AppLocale.bundle();
        val uiLocale = AppLocale.current();

        Alert detailsDialog = new Alert(Alert.AlertType.INFORMATION);
        detailsDialog.setTitle(bundle.getString(Keys.Main.DAILY_HADITH_DETAILS_TITLE));
        detailsDialog.setHeaderText(bundle.getString(Keys.Main.DAILY_HADITH_DETAILS_HEADER));
        AppLocale.applyNodeOrientation(detailsDialog.getDialogPane(), uiLocale);
        AppFonts.applyFont(detailsDialog.getDialogPane(), uiLocale);

//        String content = bundle.getString(Keys.Main.DAILY_HADITH_DETAILS_ARABIC) + ":\n" +
//                safeOrFallback(dailyHadithController.getFullHadithArabic(), bundle.getString(Keys.Main.HADITH_EXAMPLE)) +
//                "\n\n" + bundle.getString(Keys.Main.DAILY_HADITH_DETAILS_ENGLISH) + ":\n" +
//                safeOrFallback(dailyHadithController.getFullHadithEnglish(), bundle.getString(Keys.Main.EXPLANATION_EXAMPLE)) +
//                "\n\n" + bundle.getString(Keys.Main.DAILY_HADITH_DETAILS_SOURCE) + ":\n" +
//                safeOrFallback(dailyHadithController.getFullHadithSource(), bundle.getString(Keys.Main.SOURCE_EXAMPLE));

        String content = """
                Currently, there is no daily hadith content available.
                Under construction.
                """;
        detailsDialog.setContentText(content);
        detailsDialog.setResizable(true);
        detailsDialog.getDialogPane().setPrefSize(700, 540);
        detailsDialog.showAndWait();
    }

    private Optional<Remembrance> tryFetchDailyHadith() {
        try (val context = new DomainServiceContext()) {
            return context.remembranceService().findAll().stream().findAny();
        } catch (Exception _) {
            return Optional.empty();
        }
    }

    private void applyDailyHadith() {
        val fetchedRemembrance = tryFetchDailyHadith();
        if (fetchedRemembrance.isEmpty()) {
            LOGGER.error("Failed to fetch daily hadith");
            return;
        }

        val remembrance = fetchedRemembrance.get();
        val optionalTranslations = remembrance.getTranslations(AppLocale.current());

        if (optionalTranslations.isEmpty()) {
            LOGGER.error("Failed to fetch daily hadith translations");
            return;
        }

        val translations = optionalTranslations.get();

        setHadithText(translations.translationPair().text());
        setHadithExplanation(translations.explanationPair().text());
        setHadithSource(remembrance.getSource().orElse("Source not available currently."));
    }

}
