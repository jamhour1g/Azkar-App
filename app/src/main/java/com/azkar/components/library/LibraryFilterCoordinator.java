package com.azkar.components.library;

import com.azkar.components.library.model.LibraryCollectionOption;
import com.azkar.components.library.model.LibrarySortOption;
import com.azkar.components.library.service.LibraryFilteringService;
import com.azkar.components.library.ui.browse.LibraryBrowsePaneComponent;
import com.azkar.components.library.ui.toolbar.LibraryFilterToolbarComponent;
import com.azkar.components.library.ui.toolbar.LibraryMetaRowComponent;
import com.azkar.components.library.util.LabeledStringConverter;
import com.azkar.domain.model.Remembrance;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import javafx.animation.PauseTransition;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.util.Duration;
import org.controlsfx.control.ToggleSwitch;

public final class LibraryFilterCoordinator {

    @Deprecated
    public static final Duration SEARCH_DEBOUNCE = Duration.millis(180);
    private static final Duration FILTER_DEBOUNCE = Duration.millis(180);

    private final ResourceBundle bundle;
    private final LibraryFilteringService filteringService;
    private final PauseTransition filterDebounce = new PauseTransition(FILTER_DEBOUNCE);

    private final TextField searchField;
    private final ToggleSwitch favoritesOnlyToggle;
    private final ComboBox<LibraryCollectionOption> collectionCombo;
    private final TabPane categoriesTabs;
    private final Tab allTab;
    private final Tab favoritesTab;
    private final ChoiceBox<LibrarySortOption> sortChoice;
    private final Label resultCountLabel;

    LibraryFilterCoordinator(
            ResourceBundle bundle,
            LibraryFilteringService filteringService,
            LibraryFilterToolbarComponent filterToolbarComponent,
            LibraryMetaRowComponent metaRowComponent,
            LibraryBrowsePaneComponent browsePaneComponent) {
        this.bundle = bundle;
        this.filteringService = filteringService;

        searchField = filterToolbarComponent.getSearchField();
        favoritesOnlyToggle = filterToolbarComponent.getFavoritesOnlyToggle();
        collectionCombo = filterToolbarComponent.getCollectionCombo();

        resultCountLabel = metaRowComponent.getResultCountLabel();
        sortChoice = metaRowComponent.getSortChoice();

        categoriesTabs = browsePaneComponent.getCategoriesTabs();
        allTab = browsePaneComponent.getAllTab();
        favoritesTab = browsePaneComponent.getFavoritesTab();
    }

    void initialize(Runnable onFiltersRequested) {
        filterDebounce.setOnFinished(event -> onFiltersRequested.run());

        categoriesTabs
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldTab, newTab) -> onFiltersRequested.run());
        searchField.textProperty().addListener((observable, oldText, newText) -> filterDebounce.playFromStart());
        collectionCombo.valueProperty().addListener((observable, oldOption, newOption) -> onFiltersRequested.run());
        collectionCombo.setConverter(new LabeledStringConverter<>(LibraryCollectionOption::label, collectionCombo::getItems));

        favoritesOnlyToggle.selectedProperty().addListener((observable, wasSelected, isSelected) -> onFiltersRequested.run());

        sortChoice
                .getItems()
                .setAll(
                        LibrarySortOption.recent(bundle.getString("librarySortRecent")),
                        LibrarySortOption.category(bundle.getString("librarySortCategory")),
                        LibrarySortOption.source(bundle.getString("librarySortSource")),
                        LibrarySortOption.text(bundle.getString("librarySortText")));
        sortChoice.setConverter(new LabeledStringConverter<>(LibrarySortOption::label, sortChoice::getItems));
        sortChoice.getSelectionModel().selectFirst();
        sortChoice
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldSort, newSort) -> onFiltersRequested.run());

        updateResultCount(0);
    }

    void stop() {
        filterDebounce.stop();
    }

    List<LibraryCollectionOption> updateCollectionOptions(List<String> collectionNames) {
        LibraryCollectionOption previouslySelected = collectionCombo.getValue();
        List<LibraryCollectionOption> options = buildCollectionOptions(collectionNames);

        collectionCombo.getItems().setAll(options);
        if (previouslySelected != null) {
            Optional<LibraryCollectionOption> matched = options.stream()
                    .filter(option -> option.key().equals(previouslySelected.key()))
                    .findFirst();
            if (matched.isPresent()) {
                collectionCombo.getSelectionModel().select(matched.get());
                return options;
            }
        }

        if (!options.isEmpty()) {
            collectionCombo.getSelectionModel().selectFirst();
        }
        return options;
    }

    List<LibraryCollectionOption> collectionOptions() {
        return List.copyOf(collectionCombo.getItems());
    }

    List<Remembrance> apply(List<Remembrance> allRemembrances) {
        String query = Optional.ofNullable(searchField.getText()).orElse("");
        LibraryCollectionOption selectedCollection = Optional.ofNullable(collectionCombo.getValue())
                .orElse(LibraryCollectionOption.all(bundle.getString("libraryCollectionAll")));
        boolean favoritesOnly = favoritesOnlyToggle.isSelected();
        Tab selectedTab = categoriesTabs.getSelectionModel().getSelectedItem();
        LibrarySortOption selectedSort = Optional.ofNullable(sortChoice.getValue())
                .orElse(LibrarySortOption.recent(bundle.getString("librarySortRecent")));

        List<Remembrance> filtered = filteringService.apply(
                allRemembrances,
                query,
                favoritesOnly,
                selectedCollection,
                item -> matchesTab(item, selectedTab),
                selectedSort);
        updateResultCount(filtered.size());
        return filtered;
    }

    private List<LibraryCollectionOption> buildCollectionOptions(List<String> collectionNames) {
        List<LibraryCollectionOption> options = new ArrayList<>();
        options.add(LibraryCollectionOption.all(bundle.getString("libraryCollectionAll")));
        options.add(LibraryCollectionOption.favorites(bundle.getString("libraryCollectionFavorites")));
        options.addAll(collectionNames.stream().map(LibraryCollectionOption::tag).toList());
        options.add(LibraryCollectionOption.uncategorized(bundle.getString("libraryCategoryUncategorized")));
        return options;
    }

    private boolean matchesTab(Remembrance remembrance, Tab selectedTab) {
        if (selectedTab == null || selectedTab == allTab) {
            return true;
        }
        if (selectedTab == favoritesTab) {
            return remembrance.isFavorite();
        }
        return remembrance.getTags().stream().anyMatch(tag -> tag.getName().equalsIgnoreCase(selectedTab.getText()));
    }

    private void updateResultCount(int count) {
        resultCountLabel.setText(bundle.getString("libraryResultCountPrefix") + " " + count);
    }
}
