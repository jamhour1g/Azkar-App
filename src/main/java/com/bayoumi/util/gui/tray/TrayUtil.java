package com.bayoumi.util.gui.tray;

import com.bayoumi.util.Constants;
import com.bayoumi.util.LoggerWrapper;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TrayUtil {

    private final Stage stage;
    private java.awt.SystemTray tray;
    private TrayIcon trayIcon;

    private static final Logger LOGGER = LoggerWrapper.loggerFactory(TrayUtil.class);


    public TrayUtil(Stage stage) {
        // stores a reference to the stage.
        this.stage = stage;
        // instructs the javafx system [ not ] to exit implicitly when the last application window is shut.
        Platform.setImplicitExit(false);
        // sets up the tray icon (using awt code run on the swing thread).
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                addAppToTray();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error adding app to system tray", e);
                Platform.setImplicitExit(true);
            }
        });
        stage.setOnCloseRequest(event -> {
            LOGGER.info(() -> "onCloseRequest");
            if (event.getEventType().equals(WindowEvent.WINDOW_CLOSE_REQUEST)) {
                if (Platform.isImplicitExit()) {
                    if (tray != null) {
                        tray.remove(trayIcon);
                    }
                }
                this.stage.hide();
                event.consume();
            } else {
                LOGGER.info(() -> "onCloseRequest else");
            }
        });
    }

    private void addAppToTray() throws Exception {
        try {
            // ensure awt toolkit is initialized.
            java.awt.Toolkit.getDefaultToolkit();

            // app requires system tray support, just exit if there is no support.
            if (!java.awt.SystemTray.isSupported()) {
                throw new Exception("No system tray support, application exiting.");
            }

            // set up a system tray icon.
            tray = java.awt.SystemTray.getSystemTray();
            BufferedImage trayIconImage = ImageIO.read(Objects.requireNonNull(TrayUtil.class.getResource("/com/bayoumi/images/logo_50x50.png")));
            int trayIconWidth = new TrayIcon(trayIconImage).getSize().width;
            trayIcon = new TrayIcon(trayIconImage.getScaledInstance(trayIconWidth, -1, Image.SCALE_SMOOTH));
            trayIcon.setToolTip(Constants.APP_NAME + " App");
            // if the user clicks on the tray icon, show the main app stage.
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1) {
                        Platform.runLater(TrayUtil.this::showStage);
                    }
                }
            });

            // set up the popup menu for the application.
            createPopupMenu();

            // add the application tray icon to the system tray.
            tray.add(trayIcon);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error adding app to system tray", ex);
            throw ex;
        }
    }


    /**
     * setup the popup menu for the application.
     */
    private void createPopupMenu() {
        // if the user selects the default menu item (which includes the app name),
        // show the main app stage.
        java.awt.MenuItem openItem = new java.awt.MenuItem("Open");
        openItem.addActionListener(event -> Platform.runLater(this::showStage));

        // the convention for tray icons seems to be to set the default icon for opening
        // the application stage in a bold font.
        openItem.setFont(java.awt.Font.decode(null).deriveFont(java.awt.Font.BOLD));

        /*
        java.awt.MenuItem openPrayerTimes = new java.awt.MenuItem("Prayer Times");
        openPrayerTimes.addActionListener(event -> Platform.runLater(() -> Launcher.homeController.goToPrayerTimes()));

        java.awt.MenuItem morningAzkar = new java.awt.MenuItem("Morning Azkar");
        morningAzkar.addActionListener(event -> Platform.runLater(() -> Launcher.homeController.goToMorningAzkar()));

        java.awt.MenuItem nightAzkar = new java.awt.MenuItem("Night Azkar");
        nightAzkar.addActionListener(event -> Platform.runLater(() -> Launcher.homeController.goToNightAzkar()));
        */

        // to really exit the application, the user must go to the system tray icon
        // and select the exit option, this will shutdown JavaFX and remove the
        // tray icon (removing the tray icon will also shut down AWT).
        java.awt.MenuItem exitItem = new java.awt.MenuItem("Exit");
        exitItem.addActionListener(event -> {
            LOGGER.info(() -> "exitItem");
            tray.remove(trayIcon);
        });

        // setup the popup menu for the application.
        final java.awt.PopupMenu popup = new java.awt.PopupMenu();
        popup.add(openItem);
        popup.addSeparator();
        /*
        popup.add(openPrayerTimes);
        popup.addSeparator();
        popup.add(morningAzkar);
        popup.add(nightAzkar);
        popup.addSeparator();
        */
        popup.add(exitItem);
        trayIcon.setPopupMenu(popup);
    }


    /**
     * Shows the application stage and ensures that it is brought ot the front
     * of all stages.
     */
    private void showStage() {
        if (stage != null) {
            this.stage.show();
            this.stage.toFront();
        }
    }


}
