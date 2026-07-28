package com.azkar.components.library.ui.browse;

import java.io.IOException;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

@SuppressWarnings("unused")
public class LibraryGridPaneComponent extends VBox {

    private Runnable onPreviousPageAction;
    private Runnable onNextPageAction;

    @FXML
    private ScrollPane gridScrollPane;

    @FXML
    private FlowPane cardsFlowPane;

    @FXML
    private Button gridPrevButton;

    @FXML
    private Button gridNextButton;

    @FXML
    private Label gridPageLabel;

    private LibraryGridPaneComponent(String bundleName) {
        var loadedBundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/library/library_grid_pane_component.fxml"), loadedBundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load library grid pane component", exception);
        }
    }

    public LibraryGridPaneComponent() {
        this("com.azkar.i18n.home");
    }

    public void setOnPreviousPageAction(Runnable onPreviousPageAction) {
        this.onPreviousPageAction = onPreviousPageAction;
    }

    public void setOnNextPageAction(Runnable onNextPageAction) {
        this.onNextPageAction = onNextPageAction;
    }

    public ScrollPane getGridScrollPane() {
        return gridScrollPane;
    }

    public FlowPane getCardsFlowPane() {
        return cardsFlowPane;
    }

    public Button getGridPrevButton() {
        return gridPrevButton;
    }

    public Button getGridNextButton() {
        return gridNextButton;
    }

    public Label getGridPageLabel() {
        return gridPageLabel;
    }

    @FXML
    private void onPrevClicked() {
        if (onPreviousPageAction != null) {
            onPreviousPageAction.run();
        }
    }

    @FXML
    private void onNextClicked() {
        if (onNextPageAction != null) {
            onNextPageAction.run();
        }
    }
}
