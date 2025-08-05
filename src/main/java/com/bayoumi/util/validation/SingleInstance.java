package com.bayoumi.util.validation;

import com.bayoumi.util.LoggerWrapper;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Bayoumi
 */
public class SingleInstance {

    private static SingleInstance singleInstance = null;
    private final int PORT = 12347;
    private ServerSocket server;
    private Stage currentStage;
    private static final Logger LOGGER = LoggerWrapper.loggerFactory(SingleInstance.class);


    private SingleInstance() {
        singleInstanceApplicationCheck();
    }

    public static SingleInstance getInstance() {
        if (singleInstance == null) {
            singleInstance = new SingleInstance();
        }
        return singleInstance;
    }

    public Stage getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(Stage currentStage) {
        this.currentStage = currentStage;
    }

    private void singleInstanceApplicationCheck() {
        try {
            InetAddress localAddress = InetAddress.getLocalHost();
            LOGGER.info(() -> "InetAddress.getLocalHost(): " + localAddress + ":" + PORT);
            server = new ServerSocket(PORT, 1, localAddress);
            LOGGER.info(() -> "ServerSocket: " + server);
            // listen to other Instances
            new Thread(() -> {
                try {
                    while (true) {
                        Socket serverClient = server.accept();  //server accept the client connection request
                        listenToInstance(serverClient);
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Error in => listen to other Instances", ex);
                }
            }).start();
        } catch (IOException iOException) {
            // port is taken -> there is a running instance
            // notify the running instance to be on Top
            sendToServer();
        } catch (Exception ex) {
            Platform.runLater(this::showAlreadyRunningError);
        }
    }

    private void listenToInstance(Socket serverClient) {
        try {
            String clientMessage;
            Scanner scan = new Scanner(serverClient.getInputStream());
            while (scan.hasNext()) {
                clientMessage = scan.next();
                LOGGER.info("clientMessage: " + clientMessage);
                Platform.runLater(this::openCurrentStage);
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error in => listen to other Instances", ex);
        }
    }

    private void sendToServer() {
        try {
            final Socket socket = new Socket(InetAddress.getLocalHost(), PORT);
            LOGGER.info(() -> "Socket: " + socket);
            try {
                PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);
                String clientMessage = "1";
                pw.println(clientMessage);
                System.exit(0);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error in => sendToServer", e);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in => sendToServer", e);
            Platform.runLater(this::showAlreadyRunningError);
        }
    }

    private void showAlreadyRunningError() {
        // catch anything unexpected !
        LOGGER.log(Level.SEVERE, "Error in => showAlreadyRunningError");
        Alert warning = new Alert(Alert.AlertType.WARNING);
        warning.setHeaderText("Program already running, exiting");
        warning.showAndWait();
        System.exit(0);
    }

    public void openCurrentStage() {
        Platform.runLater(() -> {
            if (currentStage == null) {
                LOGGER.info(() -> "currentStage == null");
                System.exit(0);
            } else if (currentStage.isIconified()) {
                LOGGER.info(() -> "currentStage.isIconified()");
                currentStage.setIconified(false);
            } else if (!currentStage.isShowing()) {
                LOGGER.info(() -> "!currentStage.isShowing()");
                currentStage.show();
                currentStage.toFront();
            } else {
                LOGGER.info(() -> "setAlwaysOnTop");
                currentStage.setAlwaysOnTop(true);
                currentStage.setAlwaysOnTop(false);
            }
        });
    }
}
