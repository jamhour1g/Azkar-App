package com.bayoumi.util.web;

import com.bayoumi.util.LoggerWrapper;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Unirest;

import java.io.File;
import java.util.logging.Logger;

public class FileDownloader {
    private static final Logger LOGGER = LoggerWrapper.loggerFactory(FileDownloader.class);

    public static boolean downloadFile(String fileURL, File destinationFile) {
        if (destinationFile == null || fileURL == null || fileURL.isEmpty()) {
            LOGGER.info(() -> "Invalid file URL or destination file");
            return false;
        }
        try {
            HttpResponse<File> response = Unirest.get(fileURL).asFile(destinationFile.getPath());
            if (response.getStatus() == 200) {
                return true;
            } else {
                LOGGER.info(() -> "Server returned non-OK status: " + response.getStatus());
                return false;
            }
        } catch (Exception e) {
            LOGGER.info(() -> "Failed to download file: " + fileURL);
            return false;
        }
    }

}
