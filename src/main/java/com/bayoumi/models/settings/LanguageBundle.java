package com.bayoumi.models.settings;

import com.bayoumi.storage.preferences.PreferencesType;

import java.util.Observable;
import java.util.ResourceBundle;

public class LanguageBundle extends Observable {

    private ResourceBundle resourceBundle;
    private static LanguageBundle instance = null;

    public static LanguageBundle getInstance() {
        if (instance == null) {
            instance = new LanguageBundle();
        }
        return instance;
    }

    private LanguageBundle() {
        this.resourceBundle = ResourceBundle.getBundle("bundles.language");
        Settings.getInstance().addObserver(PreferencesType.LANGUAGE, (key, value) ->
                setResourceBundle(ResourceBundle.getBundle("bundles.language")));
    }

    public ResourceBundle getResourceBundle() {
        return resourceBundle;
    }

    public void setResourceBundle(ResourceBundle resourceBundle) {
        this.resourceBundle = resourceBundle;
        this.setChanged();
        this.notifyObservers();
    }
}
