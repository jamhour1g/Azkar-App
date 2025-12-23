package com.azkar;

import java.util.ResourceBundle;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.SneakyThrows;

public class Home extends Application {

    @SneakyThrows
    @Override
    public void start(Stage stage) {
        var bundle = ResourceBundle.getBundle("com.azkar.i18n.home");

        var fxmlLoader = new FXMLLoader(
            Home.class.getResource("/com/azkar/view/main_screen.fxml"),
            bundle
        );
        var scene = new Scene(
            fxmlLoader.load(),
            stage.getMaxWidth() / 2,
            stage.getMaxHeight() / 2
        );

        stage.setTitle("Azkar");
        stage.setScene(scene);
        stage.show();
        stage.centerOnScreen();
    }
}
