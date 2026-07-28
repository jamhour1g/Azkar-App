package com.azkar.components.library.util;

import com.azkar.components.library.model.LibraryCollectionDialogResult;
import com.azkar.components.library.model.LibraryCollectionOption;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;

public final class LibraryCollectionDialogSupport {

    private final ResourceBundle bundle;

    public LibraryCollectionDialogSupport(ResourceBundle bundle) {
        this.bundle = bundle;
    }

    public Optional<LibraryCollectionDialogResult> showDialog(
            List<LibraryCollectionOption> availableCollections, Consumer<Dialog<?>> dialogStyler) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(bundle.getString("libraryDialogCollectionTitle"));
        dialog.setHeaderText(bundle.getString("libraryDialogCollectionHeader"));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialogStyler.accept(dialog);

        ComboBox<String> tagCombo = buildTagCombo(availableCollections);
        TextField newCollectionField = buildNewCollectionField();
        Label validationLabel = buildValidationLabel();
        ToggleButton reminderCollectionToggle =
                buildToggleButton(bundle.getString("libraryDialogAddToReminderCollection"));
        ToggleButton reminderFavoritesToggle =
                buildToggleButton(bundle.getString("libraryDialogAddToReminderFavorites"));

        VBox content =
                buildDialogContent(
                        tagCombo,
                        newCollectionField,
                        validationLabel,
                        reminderCollectionToggle,
                        reminderFavoritesToggle);
        dialog.getDialogPane().setContent(content);
        installValidation(dialog, validationLabel, tagCombo, newCollectionField);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return Optional.empty();
        }

        return Optional.of(new LibraryCollectionDialogResult(
                resolveCollectionToApply(newCollectionField, tagCombo),
                reminderCollectionToggle.isSelected(),
                reminderFavoritesToggle.isSelected()));
    }

    private ComboBox<String> buildTagCombo(List<LibraryCollectionOption> availableCollections) {
        ComboBox<String> tagCombo = new ComboBox<>();
        tagCombo.getStyleClass().add("library-dialog-combo");
        availableCollections.stream()
                .filter(LibraryCollectionOption::isTagCollection)
                .map(LibraryCollectionOption::label)
                .forEach(tagCombo.getItems()::add);
        tagCombo.getSelectionModel().selectFirst();
        return tagCombo;
    }

    private TextField buildNewCollectionField() {
        TextField newCollectionField = new TextField();
        newCollectionField.getStyleClass().add("library-dialog-input");
        newCollectionField.setPromptText(bundle.getString("libraryDialogCollectionPrompt"));
        return newCollectionField;
    }

    private ToggleButton buildToggleButton(String label) {
        ToggleButton toggle = new ToggleButton(label);
        toggle.getStyleClass().add("library-dialog-toggle");
        return toggle;
    }

    private Label buildValidationLabel() {
        Label validationLabel = new Label();
        validationLabel.getStyleClass().add("library-dialog-validation");
        validationLabel.setWrapText(true);
        validationLabel.setManaged(false);
        validationLabel.setVisible(false);
        return validationLabel;
    }

    private VBox buildDialogContent(
            ComboBox<String> tagCombo,
            TextField newCollectionField,
            Label validationLabel,
            ToggleButton reminderCollectionToggle,
            ToggleButton reminderFavoritesToggle) {
        Label collectionPickLabel = new Label(bundle.getString("libraryDialogCollectionPick"));
        collectionPickLabel.getStyleClass().add("library-dialog-label");

        Label collectionCreateLabel = new Label(bundle.getString("libraryDialogCollectionOrCreate"));
        collectionCreateLabel.getStyleClass().add("library-dialog-label");

        VBox content = new VBox(
                10,
                collectionPickLabel,
                tagCombo,
                collectionCreateLabel,
                newCollectionField,
                validationLabel,
                reminderCollectionToggle,
                reminderFavoritesToggle);
        content.getStyleClass().add("library-dialog-content");
        content.setPadding(new Insets(10));
        return content;
    }

    private void installValidation(
            Dialog<ButtonType> dialog,
            Label validationLabel,
            ComboBox<String> tagCombo,
            TextField newCollectionField) {
        var okNode = dialog.getDialogPane().lookupButton(ButtonType.OK);
        if (!(okNode instanceof Button okButton)) {
            return;
        }

        Runnable clearValidation = () -> {
            validationLabel.setText("");
            validationLabel.setManaged(false);
            validationLabel.setVisible(false);
        };
        Runnable showValidation = () -> {
            validationLabel.setText(bundle.getString("libraryDialogCollectionValidationRequired"));
            validationLabel.setManaged(true);
            validationLabel.setVisible(true);
        };
        Runnable hideValidationWhenValid = () -> {
            if (!validationLabel.isVisible()) {
                return;
            }
            if (resolveCollectionToApply(newCollectionField, tagCombo) != null) {
                clearValidation.run();
            }
        };

        okButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (resolveCollectionToApply(newCollectionField, tagCombo) == null) {
                showValidation.run();
                newCollectionField.requestFocus();
                event.consume();
                return;
            }
            clearValidation.run();
        });

        newCollectionField
                .textProperty()
                .addListener((observable, oldValue, newValue) -> hideValidationWhenValid.run());
        tagCombo.valueProperty().addListener((observable, oldValue, newValue) -> hideValidationWhenValid.run());
    }

    private String resolveCollectionToApply(TextField newCollectionField, ComboBox<String> tagCombo) {
        String explicitNewCollection =
                Optional.ofNullable(newCollectionField.getText()).orElse("").trim();
        if (!explicitNewCollection.isBlank()) {
            return explicitNewCollection;
        }
        String selected = tagCombo.getValue();
        if (selected != null && !selected.isBlank()) {
            return selected;
        }
        return null;
    }
}
