package com.azkar.components.library.util;

import com.azkar.components.library.model.NotificationPriority;
import com.azkar.i18n.AppFonts;
import com.azkar.i18n.AppLocale;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;

public final class LibraryOverlaySupport {

    private final Locale uiLocale;
    private final ScheduledExecutorService popupDismissExecutor;

    public LibraryOverlaySupport(Locale uiLocale, ScheduledExecutorService popupDismissExecutor) {
        this.uiLocale = uiLocale;
        this.popupDismissExecutor = popupDismissExecutor;
    }

    public void styleDialog(Dialog<?> dialog, Scene ownerScene) {
        String stylesheet = LibraryOverlaySupport.class
                .getResource("/com/azkar/styles/library/library_overlay.css")
                .toExternalForm();
        var dialogPane = dialog.getDialogPane();
        AppLocale.applyNodeOrientation(dialogPane, uiLocale);
        AppFonts.applyFont(dialogPane, uiLocale);
        if (!dialogPane.getStylesheets().contains(stylesheet)) {
            dialogPane.getStylesheets().add(stylesheet);
        }
        if (!dialogPane.getStyleClass().contains("library-dialog-pane")) {
            dialogPane.getStyleClass().add("library-dialog-pane");
        }

        inheritOwnerTheme(dialog, ownerScene);
    }

    public void showNotificationPopup(String title, String message, NotificationPriority priority, Scene ownerScene) {
        if (ownerScene == null) {
            return;
        }

        Window ownerWindow = ownerScene.getWindow();
        if (ownerWindow == null) {
            return;
        }

        Popup popup = new Popup();
        popup.setAutoFix(true);
        popup.setAutoHide(true);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("library-toast-title");

        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(320);
        messageLabel.getStyleClass().add("library-toast-message");

        VBox content = new VBox(6, titleLabel, messageLabel);
        NotificationPriority resolvedPriority = NotificationPriority.fallback(priority);
        content.getStyleClass().addAll("library-toast", resolvedPriority.toastStyleClass());
        content.setPadding(new Insets(12));
        AppLocale.applyNodeOrientation(content, uiLocale);
        AppFonts.applyFont(content, uiLocale);

        popup.getContent().add(content);
        double popupX =
                AppLocale.isRtl(uiLocale) ? ownerWindow.getX() + 40 : ownerWindow.getX() + ownerWindow.getWidth() - 360;
        popup.show(ownerWindow, popupX, ownerWindow.getY() + ownerWindow.getHeight() - 170);

        ScheduledFuture<?> dismissalFuture =
                popupDismissExecutor.schedule(() -> Platform.runLater(popup::hide), 6, TimeUnit.SECONDS);
        if (dismissalFuture.isCancelled()) {
            popup.hide();
        }
    }

    private void inheritOwnerTheme(Dialog<?> dialog, Scene ownerScene) {
        if (ownerScene == null) {
            return;
        }

        Window ownerWindow = ownerScene.getWindow();
        if (ownerWindow != null) {
            dialog.initOwner(ownerWindow);
        }

        ChangeListener<ColorScheme> ownerThemeListener = (observable, oldScheme, newScheme) -> {
            Scene dialogScene = dialog.getDialogPane().getScene();
            if (dialogScene != null) {
                dialogScene.getPreferences().setColorScheme(newScheme);
            }
        };

        dialog.setOnShown(event -> {
            Scene dialogScene = dialog.getDialogPane().getScene();
            if (dialogScene != null) {
                dialogScene
                        .getPreferences()
                        .setColorScheme(ownerScene.getPreferences().getColorScheme());
            }
            ownerScene.getPreferences().colorSchemeProperty().addListener(ownerThemeListener);
        });

        dialog.setOnHidden(
                event -> ownerScene.getPreferences().colorSchemeProperty().removeListener(ownerThemeListener));
    }
}
