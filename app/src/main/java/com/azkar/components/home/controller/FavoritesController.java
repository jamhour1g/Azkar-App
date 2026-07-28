package com.azkar.components.home.controller;

import com.azkar.components.home.FavoritesCardComponent;
import com.azkar.data.config.DomainServiceContext;
import com.azkar.domain.model.Remembrance;
import com.azkar.i18n.RemembranceI18n;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ThreadLocalRandom;

public class FavoritesController {

    private final ResourceBundle bundle;
    private final Locale uiLocale;

    private List<Remembrance> favoriteRemembrances = List.of();
    private int currentFavoriteIndex = -1;

    public FavoritesController(ResourceBundle bundle, Locale uiLocale) {
        this.bundle = bundle;
        this.uiLocale = uiLocale;
    }

    public void loadFavorites(FavoritesCardComponent cardComponent) {
        try (var context = new DomainServiceContext()) {
            List<Remembrance> favorites = context.remembranceService().findFavorites();
            applyFavoriteSelection(favorites, cardComponent);
        } catch (Exception e) {
            applyFallback(cardComponent);
        }
    }

    public void showPrevious() {
        stepSelection(-1);
    }

    public void showNext() {
        stepSelection(1);
    }

    private void stepSelection(int delta) {
        if (favoriteRemembrances.isEmpty()) return;
        currentFavoriteIndex = Math.floorMod(currentFavoriteIndex + delta, favoriteRemembrances.size());
    }

    public void applyCurrentFavorite(FavoritesCardComponent cardComponent) {
        if (favoriteRemembrances.isEmpty() || currentFavoriteIndex < 0) {
            applyFallback(cardComponent);
            return;
        }
        applyFavorite(favoriteRemembrances.get(currentFavoriteIndex), cardComponent);
    }

    private void applyFavoriteSelection(List<Remembrance> favorites, FavoritesCardComponent cardComponent) {
        if (favorites == null || favorites.isEmpty()) {
            applyFallback(cardComponent);
            return;
        }

        favoriteRemembrances = List.copyOf(favorites);
        int initialIndex = ThreadLocalRandom.current().nextInt(favorites.size());
        currentFavoriteIndex = Math.floorMod(initialIndex, favoriteRemembrances.size());
        cardComponent.setNavigationEnabled(favoriteRemembrances.size() > 1);
        applyFavorite(favoriteRemembrances.get(currentFavoriteIndex), cardComponent);
    }

    private void applyFavorite(Remembrance remembrance, FavoritesCardComponent cardComponent) {
        if (remembrance == null) {
            applyFallback(cardComponent);
            return;
        }

        RemembranceI18n.LocalizedTexts localizedTexts = RemembranceI18n.resolveTexts(
                remembrance,
                uiLocale,
                bundle.getString("favoriteAzkarArabic"),
                bundle.getString("favoriteAzkarEnglish"));
        String arabic = localizedTexts.translation();
        String english = localizedTexts.explanation();
        String source = remembrance.getSource().orElse(bundle.getString("favoriteAzkarSource"));

        cardComponent.setFavoriteArabicText(arabic);
        cardComponent.setFavoriteEnglishText(english);
        cardComponent.setFavoriteSourceText(source);
    }

    private void applyFallback(FavoritesCardComponent cardComponent) {
        favoriteRemembrances = List.of();
        currentFavoriteIndex = -1;
        cardComponent.setNavigationEnabled(false);
        cardComponent.setFavoriteArabicText(bundle.getString("favoriteAzkarArabic"));
        cardComponent.setFavoriteEnglishText(bundle.getString("favoriteAzkarEnglish"));
        cardComponent.setFavoriteSourceText(bundle.getString("favoriteAzkarSource"));
    }
}
