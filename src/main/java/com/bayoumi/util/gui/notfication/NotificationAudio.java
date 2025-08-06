package com.bayoumi.util.gui.notfication;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.net.URL;
import java.util.List;
import java.util.Optional;

public class NotificationAudio implements AutoCloseable {

    public static final NotificationAudio SILENT_NOTIFICATION = new NotificationAudio("بدون صوت", 0);
    private final String fileName;
    private static final List<NotificationAudio> NOTIFICATION_AUDIOS = List.of(
            new NotificationAudio("notification01.mp3", 50),
            new NotificationAudio("notification02.mp3", 50),
            new NotificationAudio("juntos.mp3", 50),
            new NotificationAudio("swiftly.mp3", 50),
            SILENT_NOTIFICATION // TODO: change it to english later just to avoid changing all the code
    );
    private final String filePath;
    private MediaPlayer mediaPlayer;


    private NotificationAudio(String fileName, int volume) {

        if (volume < 0 || volume > 100) {
            throw new IllegalArgumentException("Volume must be between 0 and 100");
        }

        this.fileName = fileName;
        if (fileName.equals("بدون صوت")) {
            filePath = null;
            return;
        }

        URL audioResource = NotificationAudio.class.getResource("/audio/" + fileName);
        if (audioResource == null) {
            throw new IllegalArgumentException("Audio file not found: " + fileName);
        }

        filePath = audioResource.toString();

        mediaPlayer = new MediaPlayer(new Media(filePath));
        mediaPlayer.setVolume(volume);
        mediaPlayer.play();
    }

    public static List<NotificationAudio> getAudios() {
        return NOTIFICATION_AUDIOS;
    }

    @Override
    public void close() {
        if (this.mediaPlayer == null) return;

        this.mediaPlayer.stop();
        this.mediaPlayer.dispose();
        this.mediaPlayer = null;
    }

    public String getFileName() {
        return fileName;
    }

    public Optional<String> getFilePath() {
        return Optional.ofNullable(filePath);
    }

}
