package com.bayoumi.controllers.components.audio;

import com.bayoumi.models.Muezzin;
import com.bayoumi.models.settings.Settings;
import com.bayoumi.util.LoggerWrapper;
import com.bayoumi.util.gui.load.Locations;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.Pane;

import java.util.List;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChooseAudioUtil {

    private static final Random RANDOM = new Random();
    private static final Logger LOGGER = LoggerWrapper.loggerFactory(ChooseAudioUtil.class);


    public static ChooseAudioController adhan(ResourceBundle bundle, Pane container) {
        try {
            List<Muezzin> muezzinList = Muezzin.getMuezzinList();
            FXMLLoader loader = new FXMLLoader(ChooseAudioUtil.class.getResource(Locations.ChooseAudio.getName()));
            container.getChildren().add(1, loader.load());
            ChooseAudioController chooseAudioController = loader.getController();

            Muezzin selectedMuezzin = muezzinList.stream()
                    .filter(muezzin -> muezzin.getFileName().equals(Settings.getInstance().getPrayerTimeSettings().getAdhanAudio()))
                    .findFirst()
                    .orElseGet(() -> muezzinList.get(RANDOM.nextInt(muezzinList.size())));

            chooseAudioController.setData(
                    bundle.getString("muezzin"),
                    selectedMuezzin,
                    muezzinList
            );

            return chooseAudioController;
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Loading ChooseAudio", ex);
            return null;
        }
    }
}
