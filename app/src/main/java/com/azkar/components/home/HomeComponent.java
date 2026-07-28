package com.azkar.components.home;

import com.azkar.components.home.controller.FavoritesController;
import com.azkar.components.home.controller.NotificationSettingsController;
import com.azkar.components.home.controller.PrayerTimesController;
import com.azkar.i18n.AppLocale;
import com.azkar.i18n.Keys;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;

public class HomeComponent extends javafx.scene.control.ScrollPane {

    private static final Locale uiLocale = AppLocale.current();
    private final ResourceBundle bundle = AppLocale.bundle();

    private final PrayerTimesController prayerTimesController = new PrayerTimesController(bundle, uiLocale);
    private final FavoritesController favoritesController = new FavoritesController(bundle, uiLocale);
    private final NotificationSettingsController notificationSettingsController = new NotificationSettingsController();

    private boolean closed = false;

    private final ObjectProperty<EventHandler<ActionEvent>> onOpenLibraryActionProperty = new SimpleObjectProperty<>(this, "onOpenLibraryAction");

    @FXML
    private RemainingToPrayerComponent remainingToPrayerComponent;

    @FXML
    private PrayerNotificationsToggleComponent prayerNotificationsToggleComponent;

    @FXML
    private PrayerRowComponent fajrRow;

    @FXML
    private PrayerRowComponent dhuhrRow;

    @FXML
    private PrayerRowComponent asrRow;

    @FXML
    private PrayerRowComponent maghribRow;

    @FXML
    private PrayerRowComponent ishaRow;

    @FXML
    private FavoritesCardComponent favoritesCardComponent;

    public HomeComponent() {
        super();

        var fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/home/home_component.fxml"),
                bundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load FXML", e);
        }
    }

    @FXML
    public void initialize() {
        wireComponentReferences();
        initializePrayerTimes();
        initializeNotificationSettingsController();
        loadContentAsync();
    }

    public void shutdown() {
        if (closed) return;
        closed = true;
        prayerTimesController.stopCountdownTicker();
    }

    public final ObjectProperty<EventHandler<ActionEvent>> onOpenLibraryActionProperty() {
        return onOpenLibraryActionProperty;
    }

    public final void setOnOpenLibraryAction(EventHandler<ActionEvent> value) {
        onOpenLibraryActionProperty.set(value);
    }

    public final EventHandler<ActionEvent> getOnOpenLibraryAction() {
        return onOpenLibraryActionProperty.get();
    }

    private void wireComponentReferences() {
        favoritesCardComponent.setOnBrowseAction(() -> {

        });

        favoritesCardComponent.setOnPreviousAction(() -> {
            favoritesController.showPrevious();
            favoritesController.applyCurrentFavorite(favoritesCardComponent);
        });

        favoritesCardComponent.setOnNextAction(() -> {
            favoritesController.showNext();
            favoritesController.applyCurrentFavorite(favoritesCardComponent);
        });
    }

    private void initializePrayerTimes() {
        prayerTimesController.bindPrayerRows(fajrRow, dhuhrRow, asrRow, maghribRow, ishaRow);
        prayerTimesController.startCountdownTicker(remainingToPrayerComponent);
    }

    private void initializeNotificationSettingsController() {
        notificationSettingsController.initialize(
                prayerTimesController.getPrayersByKey(),
                prayerNotificationsToggleComponent,
                enabled -> {
                }
        );
        notificationSettingsController.loadPersistedSettings();
    }

    private void loadContentAsync() {
        favoritesController.loadFavorites(favoritesCardComponent);
    }

}
