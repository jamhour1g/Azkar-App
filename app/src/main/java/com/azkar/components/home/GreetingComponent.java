package com.azkar.components.home;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.chrono.HijrahDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.ResourceBundle;

import com.azkar.i18n.AppLocale;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import lombok.val;
import org.slf4j.Logger;

public class GreetingComponent extends VBox {

    private static final DateTimeFormatter HIJRI_FORMATTER = DateTimeFormatter
            .ofPattern("dd MMMM yyyy G", AppLocale.current()) // Pattern: day month year era
            .withChronology(HijrahDate.now().getChronology())   // Use Hijrah chronology
            .withResolverStyle(ResolverStyle.STRICT);

    private static final DateTimeFormatter gregorianFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", AppLocale.current());

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(GreetingComponent.class);

    @FXML
    private Text dateText;


    public GreetingComponent() {
        super();
        val bundle = ResourceBundle.getBundle("com.azkar.i18n.home");
        val fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/home/greeting_component.fxml"),
                bundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
            LOGGER.info("Greeting component loaded");
        } catch (IOException e) {
            LOGGER.error("Failed to load Greeting component", e);
            throw new IllegalStateException("Failed to load FXML", e);
        }
    }

    @FXML
    public void initialize() {
        // Get today's Gregorian date
        LocalDate todayGregorian = LocalDate.now();
        // Convert to Hijri date
        HijrahDate hijriDate = HijrahDate.from(todayGregorian);

        // Format the Hijri date
        String formattedHijri = HIJRI_FORMATTER.format(hijriDate);
        String gregorian = gregorianFormatter.format(todayGregorian);
        dateText.setText(formattedHijri + " | " + gregorian);
        LOGGER.info("Date text set to: {} | {}", formattedHijri, gregorian);
    }

}
