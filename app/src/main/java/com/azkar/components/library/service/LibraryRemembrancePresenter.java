package com.azkar.components.library.service;

import com.azkar.components.library.model.LibraryRemembranceRow;
import com.azkar.domain.model.Remembrance;
import com.azkar.domain.model.Tag;
import com.azkar.i18n.RemembranceI18n;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public final class LibraryRemembrancePresenter {

    private static final int ROW_PREVIEW_MAX_LENGTH = 96;

    private final ResourceBundle bundle;
    private final Locale uiLocale;
    private final Map<String, String> categoryLabelKeys = Map.of(
            "morning", "libraryFilterMorning",
            "evening", "libraryFilterEvening",
            "travel", "libraryFilterTravel");

    public LibraryRemembrancePresenter(ResourceBundle bundle, Locale uiLocale) {
        this.bundle = bundle;
        this.uiLocale = uiLocale;
    }

    public LibraryRemembranceRow toRow(Remembrance remembrance) {
        long id = remembrance.getId().orElse(-1L);
        String category = primaryCategory(remembrance);
        String primary = preview(localizedPrimaryText(remembrance), ROW_PREVIEW_MAX_LENGTH);
        String secondary = preview(localizedSecondaryText(remembrance), ROW_PREVIEW_MAX_LENGTH);
        String source = remembrance.getSource().orElse(bundle.getString("librarySourceUnknown"));
        return new LibraryRemembranceRow(id, category, primary, secondary, source, remembrance.isFavorite());
    }

    public String primaryCategory(Remembrance remembrance) {
        String category = remembrance.getTags().stream()
                .map(Tag::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .findFirst()
                .orElse(bundle.getString("libraryCategoryUncategorized"));
        return localizeCategoryLabel(category);
    }

    public String localizedPrimaryText(Remembrance remembrance) {
        return localizedTexts(remembrance).translation();
    }

    public String localizedSecondaryText(Remembrance remembrance) {
        return localizedTexts(remembrance).explanation();
    }

    private String localizeCategoryLabel(String category) {
        if (category == null || category.isBlank()) {
            return bundle.getString("libraryCategoryUncategorized");
        }

        String normalized = category.trim().toLowerCase(Locale.ROOT);
        String labelKey = categoryLabelKeys.get(normalized);
        return labelKey != null ? bundle.getString(labelKey) : category;
    }

    private String textFallback(Remembrance remembrance) {
        return remembrance.getTranslations().values().stream()
                .map(Remembrance.Translations::translationPair)
                .map(Remembrance.Translations.Pair::text)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElse(bundle.getString("libraryMissingText"));
    }

    private RemembranceI18n.LocalizedTexts localizedTexts(Remembrance remembrance) {
        return RemembranceI18n.resolveTexts(
                remembrance, uiLocale, textFallback(remembrance), bundle.getString("libraryMissingText"));
    }

    private String preview(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\n", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
