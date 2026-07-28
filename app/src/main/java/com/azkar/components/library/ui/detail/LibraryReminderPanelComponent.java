package com.azkar.components.library.ui.detail;

import com.azkar.components.library.model.LibraryCollectionOption;
import com.azkar.components.library.model.NotificationCadence;
import com.azkar.components.library.model.NotificationPriority;
import com.azkar.components.library.model.ReminderSelectionMode;
import com.azkar.components.library.model.ReminderTargetOption;
import java.io.IOException;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

public class LibraryReminderPanelComponent extends VBox {

    private Runnable onScheduleAction;
    private Runnable onStopAction;
    private Runnable onApplySingleAction;
    private Runnable onAddCollectionAction;
    private Runnable onAddCustomCollectionAction;

    @FXML
    private ChoiceBox<ReminderSelectionMode> modeChoice;

    @FXML
    private VBox singleModeBox;

    @FXML
    private ChoiceBox<ReminderTargetOption> singleRemembranceChoice;

    @FXML
    private ChoiceBox<NotificationPriority> singlePriorityChoice;

    @FXML
    private Button singleApplyButton;

    @FXML
    private Label singleSelectionLabel;

    @FXML
    private VBox collectionsModeBox;

    @FXML
    private ChoiceBox<LibraryCollectionOption> collectionChoice;

    @FXML
    private ChoiceBox<NotificationPriority> collectionPriorityChoice;

    @FXML
    private Button collectionAddButton;

    @FXML
    private FlowPane collectionChipsPane;

    @FXML
    private VBox customModeBox;

    @FXML
    private TextField customCollectionField;

    @FXML
    private ChoiceBox<NotificationPriority> customPriorityChoice;

    @FXML
    private Button customAddButton;

    @FXML
    private FlowPane customChipsPane;

    @FXML
    private ChoiceBox<NotificationCadence> cadenceChoice;

    @FXML
    private Spinner<Integer> notificationsPerCycleSpinner;

    @FXML
    private ToggleButton randomOrderToggle;

    @FXML
    private Button scheduleButton;

    @FXML
    private Button stopButton;

    @FXML
    private Label schedulerStatusLabel;

    @FXML
    private Label reminderCollectionCountLabel;

    @FXML
    private Label reminderFavoritesCountLabel;

    @FXML
    private Label schedulerPreviewLabel;

    private LibraryReminderPanelComponent(String bundleName) {
        var loadedBundle = ResourceBundle.getBundle(bundleName);
        var fxmlLoader = new FXMLLoader(
                getClass().getResource("/com/azkar/components/library/library_reminder_panel_component.fxml"),
                loadedBundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load library reminder panel component", exception);
        }
    }

    public LibraryReminderPanelComponent() {
        this("com.azkar.i18n.home");
    }

    public ChoiceBox<NotificationCadence> getCadenceChoice() {
        return cadenceChoice;
    }

    public ChoiceBox<ReminderSelectionMode> getModeChoice() {
        return modeChoice;
    }

    public VBox getSingleModeBox() {
        return singleModeBox;
    }

    public ChoiceBox<ReminderTargetOption> getSingleRemembranceChoice() {
        return singleRemembranceChoice;
    }

    public ChoiceBox<NotificationPriority> getSinglePriorityChoice() {
        return singlePriorityChoice;
    }

    public Button getSingleApplyButton() {
        return singleApplyButton;
    }

    public Label getSingleSelectionLabel() {
        return singleSelectionLabel;
    }

    public VBox getCollectionsModeBox() {
        return collectionsModeBox;
    }

    public ChoiceBox<LibraryCollectionOption> getCollectionChoice() {
        return collectionChoice;
    }

    public ChoiceBox<NotificationPriority> getCollectionPriorityChoice() {
        return collectionPriorityChoice;
    }

    public Button getCollectionAddButton() {
        return collectionAddButton;
    }

    public FlowPane getCollectionChipsPane() {
        return collectionChipsPane;
    }

    public VBox getCustomModeBox() {
        return customModeBox;
    }

    public TextField getCustomCollectionField() {
        return customCollectionField;
    }

    public ChoiceBox<NotificationPriority> getCustomPriorityChoice() {
        return customPriorityChoice;
    }

    public Button getCustomAddButton() {
        return customAddButton;
    }

    public FlowPane getCustomChipsPane() {
        return customChipsPane;
    }

    public Spinner<Integer> getNotificationsPerCycleSpinner() {
        return notificationsPerCycleSpinner;
    }

    public ToggleButton getRandomOrderToggle() {
        return randomOrderToggle;
    }

    public Label getSchedulerStatusLabel() {
        return schedulerStatusLabel;
    }

    public Label getReminderCollectionCountLabel() {
        return reminderCollectionCountLabel;
    }

    public Label getReminderFavoritesCountLabel() {
        return reminderFavoritesCountLabel;
    }

    public Label getSchedulerPreviewLabel() {
        return schedulerPreviewLabel;
    }

    public void setOnScheduleAction(Runnable onScheduleAction) {
        this.onScheduleAction = onScheduleAction;
    }

    public void setOnStopAction(Runnable onStopAction) {
        this.onStopAction = onStopAction;
    }

    public void setOnApplySingleAction(Runnable onApplySingleAction) {
        this.onApplySingleAction = onApplySingleAction;
    }

    public void setOnAddCollectionAction(Runnable onAddCollectionAction) {
        this.onAddCollectionAction = onAddCollectionAction;
    }

    public void setOnAddCustomCollectionAction(Runnable onAddCustomCollectionAction) {
        this.onAddCustomCollectionAction = onAddCustomCollectionAction;
    }

    @FXML
    private void onScheduleClicked() {
        if (onScheduleAction != null) {
            onScheduleAction.run();
        }
    }

    @FXML
    private void onStopClicked() {
        if (onStopAction != null) {
            onStopAction.run();
        }
    }

    @FXML
    private void onApplySingleClicked() {
        if (onApplySingleAction != null) {
            onApplySingleAction.run();
        }
    }

    @FXML
    private void onAddCollectionClicked() {
        if (onAddCollectionAction != null) {
            onAddCollectionAction.run();
        }
    }

    @FXML
    private void onAddCustomCollectionClicked() {
        if (onAddCustomCollectionAction != null) {
            onAddCustomCollectionAction.run();
        }
    }
}
