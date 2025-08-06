package com.bayoumi.controllers.dialog;

import com.bayoumi.models.UpdateInfo;
import com.bayoumi.models.settings.LanguageBundle;
import com.jfoenix.controls.JFXButton;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.NodeOrientation;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

public class UpdateConfirmController implements Initializable {
    public boolean isConfirmed;
    @FXML
    private Label thereIsANewUpdate;

    @FXML
    private Label oldVersion;

    @FXML
    private Label oldVersionText;

    @FXML
    private Label newVersion;

    @FXML
    private Label newVersionText;

    @FXML
    private Label notesText;
    @FXML
    private Label doYouWantToUpdateTheSoftware;
    @FXML
    private JFXButton discardButton;
    @FXML
    private JFXButton confirmBTN;
    @FXML
    private TextArea comment;
    @FXML
    private VBox root;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    public void setData(UpdateInfo updateInfo, String oldVersionValue) {
        oldVersion.setText(oldVersionValue);
        if (updateInfo != null) {
            newVersion.setText(updateInfo.getVersion());
            comment.setText(updateInfo.getComment());
        }

        final ResourceBundle bundle = LanguageBundle.getInstance().getResourceBundle();
        confirmBTN.setText(bundle.getString("confirm"));
        discardButton.setText(bundle.getString("discard"));
        thereIsANewUpdate.setText(bundle.getString("thereIsANewUpdate"));
        oldVersionText.setText(bundle.getString("oldVersionText"));
        newVersionText.setText(bundle.getString("newVersionText"));
        notesText.setText(bundle.getString("notes") + ":");
        doYouWantToUpdateTheSoftware.setText(bundle.getString("doYouWantToUpdateTheSoftware"));

        root.setNodeOrientation(NodeOrientation.valueOf(bundle.getString("dir")));
    }

    @FXML
    private void confirmAction() {
        isConfirmed = true;
        ((Stage) (oldVersion.getScene().getWindow())).close();
    }

    @FXML
    private void discardAction() {
        isConfirmed = false;
        ((Stage) (oldVersion.getScene().getWindow())).close();
    }

}
