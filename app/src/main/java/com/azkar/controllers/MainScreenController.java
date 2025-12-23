package com.azkar.controllers;

import com.azkar.components.MainScreenHeader;
import com.azkar.components.home.HomeComponent;
import javafx.fxml.FXML;
import javafx.scene.layout.BorderPane;

public class MainScreenController {

    @FXML
    private BorderPane root;

    @FXML
    private MainScreenHeader header;

    @FXML
    private HomeComponent homeComponent;
}
