package com.bayoumi;

import com.bayoumi.controllers.components.audio.ChooseAudioController;
import com.bayoumi.controllers.home.HomeController;
import com.bayoumi.models.settings.Settings;
import com.bayoumi.repositry.OnboardingRepository;
import com.bayoumi.services.update.UpdateService;
import com.bayoumi.storage.DatabaseManager;
import com.bayoumi.storage.LocationsDBManager;
import com.bayoumi.storage.preferences.Preferences;
import com.bayoumi.storage.preferences.PreferencesType;
import com.bayoumi.util.Constants;
import com.bayoumi.util.LoggerWrapper;
import com.bayoumi.util.SentryUtil;
import com.bayoumi.util.gui.ArabicTextSupport;
import com.bayoumi.util.gui.BuilderUI;
import com.bayoumi.util.gui.HelperMethods;
import com.bayoumi.util.gui.load.Locations;
import com.bayoumi.util.gui.tray.TrayUtil;
import com.bayoumi.util.validation.SingleInstance;
import com.bayoumi.util.web.server.ServerService;
import com.install4j.api.launcher.StartupNotification;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import kong.unirest.core.Unirest;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Launcher extends Application {
    // GUI Object
    public static HomeController homeController;

    // GUI Object
    private Scene scene = null;

    private static final Logger LOGGER = LoggerWrapper.loggerFactory(Launcher.class);


    public static void main(String[] args) {
        ArabicTextSupport.fix();
        Application.launch();
    }

    @Override
    public void stop() throws Exception {
        LOGGER.info("stop()...");
        super.stop();
    }

    @Override
    public void init() {

        try {

            // --- initialize Logger ---
            LOGGER.info("App Starting...");

            // --- initialize Unirest ---
            Unirest.config().connectTimeout(30_000);

            // --- initialize database connection ---
            DatabaseManager databaseManager = DatabaseManager.getInstance();
            if (!databaseManager.init()) {
                stop();
            }

            // --- initialize Preferences ---
            Preferences.init();

            // --- initialize Auto Update Check ---
            UpdateService.checkForUpdate();

            // --- initialize database connection (locationsDB) ---
            try {
                LocationsDBManager.getInstance();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Initialize locationsDB failed", ex);
            }

            // --- load Homepage FXML ---
            FXMLLoader loader = new FXMLLoader(getClass().getResource(Locations.Home.toString()));
            scene = new Scene(loader.load());
            scene.getStylesheets().setAll(Settings.getInstance().getThemeFilesCSS());
            homeController = loader.getController();

            // --- initialize Sentry for error tracking ---
            try {
                SentryUtil.init();
            } catch (Exception ex) {
                LOGGER.warning(() -> "Error initializing Sentry: " + ex.getLocalizedMessage());
            }

            if (Constants.RUNNING_MODE.equals(Constants.Mode.PRODUCTION)) ServerService.init();

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "init failed", ex);
        }
    }

    private void showOnboardingIfFirstTimeOpened(boolean isFirstTimeOpened) {
        if (!isFirstTimeOpened) return;

        try {
            final Scene onboardingScene = new Scene(FXMLLoader.load(Objects.requireNonNull(getClass().getResource(Locations.Onboarding.toString()))));
            onboardingScene.getStylesheets().setAll(Settings.getInstance().getThemeFilesCSS());
            final Stage onboardingStage = BuilderUI.initStageDecorated(onboardingScene, "Onboarding - " + Constants.APP_NAME);
            onboardingStage.show();
            onboardingStage.setOnCloseRequest(event -> ChooseAudioController.stopIfPlaying());
            Preferences.getInstance().set(PreferencesType.APP_VERSION, Constants.VERSION);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Show onboarding failed", ex);
        }
    }

    private void showVersionInstalled(boolean isFirstTimeOpened, boolean isNewVersion) {
        if (isFirstTimeOpened || !isNewVersion) {
            return;
        }

        try {
            final Scene versionScene = new Scene(FXMLLoader.load(Objects.requireNonNull(getClass().getResource(Locations.VersionInstalled.toString()))));
            versionScene.getStylesheets().setAll(Settings.getInstance().getThemeFilesCSS());
            final Stage stage = BuilderUI.initStageDecorated(versionScene, Constants.APP_NAME + " - " + Constants.VERSION);
            stage.show();
            Preferences.getInstance().set(PreferencesType.APP_VERSION, Constants.VERSION);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Show versionInstalled failed", ex);
        }
    }

    public void start(Stage primaryStage) throws Exception {

        try {
            new TrayUtil(primaryStage);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Show tray failed", ex);
        }

        // add loaded scene to primaryStage
        primaryStage.setScene(scene);

        // set Title and Icon to primaryStage
        HelperMethods.SetAppDecoration(primaryStage);

        // show primaryStage
        final boolean isFirstTimeOpened = OnboardingRepository.isFirstTimeOpened();
        final boolean isNewVersion = !Constants.VERSION.equals(Preferences.getInstance().get(PreferencesType.APP_VERSION));
        if (isFirstTimeOpened || isNewVersion || !Settings.getInstance().getMinimized()) {
            primaryStage.show();
        }

        // assign current primaryStage to SingleInstance Class
        SingleInstance.getInstance().setCurrentStage(primaryStage);

        // show Onboarding stage
        showOnboardingIfFirstTimeOpened(isFirstTimeOpened);

        // show VersionInstalled stage
        showVersionInstalled(isFirstTimeOpened, isNewVersion);

        StartupNotification.registerStartupListener(s ->
                SingleInstance.getInstance().openCurrentStage());
    }
}