package com.bayoumi.util;

import com.bayoumi.storage.DatabaseManager;
import com.bayoumi.storage.preferences.Preferences;
import com.bayoumi.storage.statistics.StatisticsStore;
import kong.unirest.core.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class AppPropertiesUtil {

    public static Map<String, String> getProps() {
        final Map<String, String> props = new HashMap<>();

        // OS Information
        props.put("os.name", System.getProperty("os.name"));
        props.put("os.version", System.getProperty("os.version"));
        props.put("os.architecture", System.getProperty("os.arch"));

        // Java Information
        props.put("java.version", System.getProperty("java.version"));

        // Timezone Information
        props.put("timezone.id", TimeZone.getDefault().getID());
        props.put("timezone.name", TimeZone.getDefault().getDisplayName());
        props.put("timezone.offset_hours", String.valueOf(TimeZone.getDefault().getRawOffset() / 3600000));
        props.put("timezone.dst_savings", TimeZone.getDefault().getDSTSavings() == 0 ? "No" : "Yes");

        // Locale Information
        props.put("locale.default", Locale.getDefault().toString());

        return props;
    }

    public static String getAllAppPropsAsJsonString() {
        final JSONObject jsonObject = new JSONObject(getProps());
        jsonObject.put("id", DatabaseManager.getInstance().getID());
        Preferences.getInstance().getAllWithPrefix().forEach(jsonObject::put);
        StatisticsStore.getInstance().getAllWithPrefix().forEach(jsonObject::put);
        return jsonObject.toString();
    }

}
