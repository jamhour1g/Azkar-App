package com.azkar;

import com.azkar.controllers.MainScreenController;
import com.azkar.data.config.JpaManager;
import com.azkar.i18n.AppFonts;
import com.azkar.i18n.AppLocale;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.val;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static com.azkar.i18n.Keys.Header.TITLE;

public class Home extends Application {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(Home.class);
    private @Nullable CompletableFuture<Void> databaseMigrations;

    @Override
    public void init() {
        // Run the database migrations in a separate thread.
        // This allows the application to start and show the UI faster,
        // while the database is being prepared in the background.
        databaseMigrations = CompletableFuture.runAsync(() -> JpaManager.getInstance().warmUp());
        databaseMigrations
                .whenCompleteAsync((_, ex) -> {
                    if (ex != null) {
                        LOGGER.atError()
                                .setCause(ex)
                                .log("Database migrations failed: {}", ex.getMessage());
                        return;
                    }

                    LOGGER.info("Database migrations completed successfully");
                });
    }

    @Override
    public void start(Stage stage) throws IOException {
        LOGGER.info("Starting application");

        AppLocale.applyPersisted();
        LOGGER.info("Applied persisted locale: {}", AppLocale.current());

        LOGGER.debug("Loaded resource bundle: com.azkar.i18n.home");

        val fxmlLoader = new FXMLLoader(getLocation(), AppLocale.bundle());
        val scene = new Scene(fxmlLoader.load());

        AppLocale.applyNodeOrientation(scene.getRoot());
        AppFonts.applyFontRecursive(scene.getRoot());

        val controller = fxmlLoader.<MainScreenController>getController();
        LOGGER.debug("Loaded controller: {}", controller.getClass().getSimpleName());

        stage.setOnCloseRequest(_ -> {
            controller.shutdown();
            LOGGER.info("Application window closed");
        });

        stage.setTitle(AppLocale.bundle().getString(TITLE));

        stage.setScene(scene);
        stage.show();

        stage.centerOnScreen();
        LOGGER.info("Application window displayed");
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        LOGGER.info("Stopping application");

        if (databaseMigrations != null && !databaseMigrations.isDone()) {
            LOGGER.info("Cancelling running database migrations");
            databaseMigrations.cancel(true);
        }

        LOGGER.info("Closing database");
        JpaManager.getInstance().close();
    }

    private static URL getLocation() {
        LOGGER.debug("Loading main screen FXML from classpath");
        return Objects.requireNonNull(
                Home.class.getResource("/com/azkar/view/main_screen.fxml"),
                """
                        Failed to load view FXML resource.
                        Make sure the resource is available at /com/azkar/view/main_screen.fxml
                        """
        );
    }
}
