package com.azkar.controllers;

import com.azkar.components.MainScreenHeader;
import com.azkar.components.home.HomeComponent;
import com.azkar.components.library.LibraryComponent;
import javafx.fxml.FXML;
import javafx.scene.layout.BorderPane;

public class MainScreenController {

    @FXML
    private BorderPane root;

    @FXML
    private MainScreenHeader header;

    @FXML
    private HomeComponent homeComponent;

    private LibraryComponent libraryComponent;

    @FXML
    private void initialize() {
        header.setOnHomeAction(this::showHome);
        header.setOnAzkarLibraryAction(this::showLibrary);
        showHome();
    }

    private void showHome() {
        root.setCenter(homeComponent);
        header.setActiveTab(MainScreenHeader.HeaderTab.HOME);
    }

    private void showLibrary() {
        if (libraryComponent == null) {
            libraryComponent = new LibraryComponent();
        }
        root.setCenter(libraryComponent);
        header.setActiveTab(MainScreenHeader.HeaderTab.LIBRARY);
    }
}
