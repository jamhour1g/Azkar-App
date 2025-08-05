package com.bayoumi.services.azkar;

import com.bayoumi.controllers.home.periods.AzkarPeriodsController;
import com.bayoumi.models.azkar.AbsoluteZekr;
import com.bayoumi.models.settings.Settings;
import com.bayoumi.services.EditablePeriodTimerTask;
import com.bayoumi.services.statistics.StatisticsService;
import com.bayoumi.storage.statistics.StatisticsType;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.controlsfx.control.Notifications;

public class AzkarService {

    private static EditablePeriodTimerTask absoluteAzkarTask;
    public static Stage FAKE_STAGE;
    private static int currentZekrIndex = 0;

    public static void stopService() {
        if (AzkarService.absoluteAzkarTask != null) {
            AzkarService.absoluteAzkarTask.stopTask();
        }
    }

    public static void updateTimer() {
        if (AzkarService.absoluteAzkarTask != null) {
            AzkarService.absoluteAzkarTask.updateTimer();
        }
    }

    public static void init(AzkarPeriodsController azkarPeriodsController) {
        if (FAKE_STAGE == null) {
            Platform.runLater(() -> {
                FAKE_STAGE = new Stage(StageStyle.UTILITY);
                FAKE_STAGE.setOpacity(0);
                FAKE_STAGE.show();
                FAKE_STAGE.toBack();
            });
        }
        azkarPeriodsController.setFrequencyLabel();
        absoluteAzkarTask = null;
        absoluteAzkarTask = new EditablePeriodTimerTask(()
                -> {
            if (AbsoluteZekr.absoluteZekrObservableList.isEmpty()) {
                return;
            }

            AbsoluteZekr currentZekr;
            if (currentZekrIndex >= 0 && currentZekrIndex < AbsoluteZekr.absoluteZekrObservableList.size()) {
                currentZekr = AbsoluteZekr.absoluteZekrObservableList.get(currentZekrIndex);
            } else {
                currentZekr = AbsoluteZekr.absoluteZekrObservableList.get(0); // Fallback to the first item
            }
            StatisticsService.getInstance().increment(StatisticsType.AZKAR_NOTIFICATION_SHOWN);

            Platform.runLater(()
                    -> {
                // TODO: Fix add the notification sounds back
                Notifications.create()
                        .title(currentZekr.getText())
                        .text(currentZekr.getText())
                        .graphic(new ImageView(new Image("/com/bayoumi/images/Kaaba.png")))
                        .hideAfter(Duration.seconds(Settings.getInstance().getAzkarSettings().getAzkarDuration()))
                        .position(Settings.getInstance().getNotificationSettings().getPosition())
                        .onAction(_ -> StatisticsService.getInstance().increment(StatisticsType.AZKAR_NOTIFICATION_CLICKED))
                        .show();
            });
            currentZekrIndex = (currentZekrIndex + 1) % AbsoluteZekr.absoluteZekrObservableList.size();
        },
                azkarPeriodsController::getPeriod);
        absoluteAzkarTask.updateTimer();
    }
}
