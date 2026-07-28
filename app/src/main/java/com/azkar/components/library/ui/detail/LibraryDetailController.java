package com.azkar.components.library.ui.detail;

import com.azkar.components.library.service.LibraryRemembrancePresenter;
import com.azkar.domain.model.Remembrance;
import java.util.ResourceBundle;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.javafx.FontIcon;

public final class LibraryDetailController {

    private final ResourceBundle bundle;
    private final LibraryRemembrancePresenter remembrancePresenter;
    private final Label detailCategoryLabel;
    private final Label detailArabicLabel;
    private final Label detailEnglishLabel;
    private final Label detailSourceLabel;
    private final Button detailReadButton;
    private final Button detailCollectionButton;
    private final Button detailFavoriteButton;
    private final FontIcon detailFavoriteIcon;

    public LibraryDetailController(
            ResourceBundle bundle,
            LibraryRemembrancePresenter remembrancePresenter,
            LibraryDetailPaneComponent detailPaneComponent) {
        this.bundle = bundle;
        this.remembrancePresenter = remembrancePresenter;

        detailCategoryLabel = detailPaneComponent.getDetailCategoryLabel();
        detailArabicLabel = detailPaneComponent.getDetailArabicLabel();
        detailEnglishLabel = detailPaneComponent.getDetailEnglishLabel();
        detailSourceLabel = detailPaneComponent.getDetailSourceLabel();
        detailReadButton = detailPaneComponent.getDetailReadButton();
        detailCollectionButton = detailPaneComponent.getDetailCollectionButton();
        detailFavoriteButton = detailPaneComponent.getDetailFavoriteButton();
        detailFavoriteIcon = detailPaneComponent.getDetailFavoriteIcon();
    }

    public void initialize() {
        detailReadButton.setTooltip(new Tooltip(bundle.getString("libraryActionRead")));
        detailCollectionButton.setTooltip(new Tooltip(bundle.getString("libraryActionAddCollection")));
        detailFavoriteButton.setTooltip(new Tooltip(bundle.getString("libraryActionFavorite")));
        showRemembrance(null);
    }

    public void showRemembrance(Remembrance remembrance) {
        if (remembrance == null) {
            showEmptySelection();
            return;
        }

        detailCategoryLabel.setText(remembrancePresenter.primaryCategory(remembrance));
        detailArabicLabel.setText(remembrancePresenter.localizedPrimaryText(remembrance));
        detailEnglishLabel.setText(remembrancePresenter.localizedSecondaryText(remembrance));
        detailSourceLabel.setText(bundle.getString("librarySourceLabel") + " "
                + remembrance.getSource().orElse(bundle.getString("librarySourceUnknown")));

        detailFavoriteButton.setDisable(false);
        detailCollectionButton.setDisable(false);
        detailReadButton.setDisable(false);
        applyFavoriteActionVisualState(remembrance.isFavorite());
    }

    private void showEmptySelection() {
        detailCategoryLabel.setText(bundle.getString("libraryDetailTitle"));
        detailArabicLabel.setText(bundle.getString("libraryDetailEmpty"));
        detailEnglishLabel.setText("");
        detailSourceLabel.setText(bundle.getString("librarySourceLabel") + " -");

        detailFavoriteButton.setDisable(true);
        detailCollectionButton.setDisable(true);
        detailReadButton.setDisable(true);
        applyFavoriteActionVisualState(false);
    }

    private void applyFavoriteActionVisualState(boolean favorite) {
        detailFavoriteButton.getStyleClass().remove("library-icon-btn-favorite");
        if (favorite) {
            detailFavoriteButton.getStyleClass().add("library-icon-btn-favorite");
            detailFavoriteIcon.setIconLiteral("fas-star");
            detailFavoriteIcon.getStyleClass().setAll("library-icon-favorite");
            detailFavoriteButton.getTooltip().setText(bundle.getString("libraryActionUnfavorite"));
            return;
        }

        detailFavoriteIcon.setIconLiteral("far-star");
        detailFavoriteIcon.getStyleClass().setAll("library-icon");
        detailFavoriteButton.getTooltip().setText(bundle.getString("libraryActionFavorite"));
    }
}
