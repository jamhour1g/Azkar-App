package com.bayoumi.services.update;

import com.bayoumi.models.settings.Settings;
import com.bayoumi.util.LoggerWrapper;

import java.util.Timer;
import java.util.logging.Logger;

public class UpdateService {
    private static final Logger LOGGER = LoggerWrapper.loggerFactory(UpdateService.class);

    public static void checkForUpdate() {
        final Timer timer = new Timer();
        timer.schedule(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        LOGGER.info("[UpdateService] checkForUpdate()");
                        if (UpdateHandler.getInstance().checkUpdate() == 1 & Settings.getInstance().getAutomaticCheckForUpdates()) {
                            UpdateHandler.getInstance().showInstallPrompt();
                        }
                        // close the thread
                        timer.cancel();
                    }
                },
                390000 // 6.5min => to ensure that update will open when no notification is shown
        );
        LOGGER.info("[UpdateService] checkForUpdate() scheduled");
    }
}
