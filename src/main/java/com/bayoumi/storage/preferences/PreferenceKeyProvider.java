package com.bayoumi.storage.preferences;

import java.util.Arrays;
import java.util.Collection;

public interface PreferenceKeyProvider {

    static Collection<PreferenceEntry> toPreferenceEntries(Collection<? extends Class<? extends Enum<? extends PreferenceKeyProvider>>> enums) {

        return enums.stream()
                .flatMap(enumClass -> Arrays.stream(enumClass.getEnumConstants()))
                .map(enumConstant -> ((PreferenceKeyProvider) enumConstant).entry())
                .toList();
    }

    PreferenceEntry entry();

    record PreferenceEntry(String key, String defaultValue) {
    }

}