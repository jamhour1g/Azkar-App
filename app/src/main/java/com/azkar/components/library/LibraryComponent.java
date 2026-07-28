package com.azkar.components.library;

import com.azkar.components.library.model.LibraryCollectionDialogResult;
import com.azkar.components.library.model.LibraryCollectionOption;
import com.azkar.components.library.model.NotificationPriority;
import com.azkar.components.library.service.LibraryDataService;
import com.azkar.components.library.service.LibraryFilteringService;
import com.azkar.components.library.service.LibraryRemembrancePresenter;
import com.azkar.components.library.ui.browse.LibraryBrowseController;
import com.azkar.components.library.ui.browse.LibraryBrowsePaneComponent;
import com.azkar.components.library.ui.detail.LibraryDetailController;
import com.azkar.components.library.ui.detail.LibraryDetailPaneComponent;
import com.azkar.components.library.ui.detail.LibraryReminderPanelComponent;
import com.azkar.components.library.ui.detail.LibraryReminderPlannerController;
import com.azkar.components.library.ui.toolbar.LibraryFilterToolbarComponent;
import com.azkar.components.library.ui.toolbar.LibraryHeaderComponent;
import com.azkar.components.library.ui.toolbar.LibraryMetaRowComponent;
import com.azkar.components.library.util.LibraryCollectionDialogSupport;
import com.azkar.components.library.util.LibraryOverlaySupport;
import com.azkar.data.config.DomainServiceContext;
import com.azkar.domain.model.Remembrance;
import com.azkar.i18n.AppLocale;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.LongConsumer;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class LibraryComponent extends VBox {

    private final Locale uiLocale = Locale.getDefault();
    private final ResourceBundle bundle;
    private final LibraryRemembrancePresenter remembrancePresenter;
    private final LibraryFilteringService filteringService;
    private final LibraryDataService dataService = new LibraryDataService();
    private final ExecutorService dataExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService popupDismissExecutor = Executors.newSingleThreadScheduledExecutor();
    private final LibraryDataCoordinator dataCoordinator;
    private final LibraryOverlaySupport overlaySupport;
    private final LibraryCollectionDialogSupport collectionDialogSupport;
    private final DateTimeFormatter infoDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", uiLocale);

    private List<Remembrance> allRemembrances = List.of();
    private List<Remembrance> filteredRemembrances = List.of();
    private Remembrance selectedRemembrance;
    private boolean loading;
    private boolean closed;

    @FXML
    private LibraryHeaderComponent headerComponent;

    @FXML
    private LibraryFilterToolbarComponent filterToolbarComponent;

    @FXML
    private LibraryMetaRowComponent metaRowComponent;

    @FXML
    private LibraryBrowsePaneComponent browsePaneComponent;

    @FXML
    private LibraryDetailPaneComponent detailPaneComponent;

    @FXML
    private Label infoLine;

    private LibraryFilterCoordinator filterCoordinator;
    private LibraryBrowseController browseController;
    private LibraryDetailController detailController;
    private LibraryReminderPlannerController reminderPlannerController;

    private LibraryComponent(String bundleName) {
        bundle = ResourceBundle.getBundle(bundleName);
        remembrancePresenter = new LibraryRemembrancePresenter(bundle, uiLocale);
        filteringService = new LibraryFilteringService(
                remembrancePresenter::primaryCategory, remembrancePresenter::localizedPrimaryText);
        overlaySupport = new LibraryOverlaySupport(uiLocale, popupDismissExecutor);
        collectionDialogSupport = new LibraryCollectionDialogSupport(bundle);
        dataCoordinator = new LibraryDataCoordinator(dataService, dataExecutor, () -> closed);

        var fxmlLoader =
                new FXMLLoader(getClass().getResource("/com/azkar/components/library/library_component.fxml"), bundle);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load library component", exception);
        }
    }

    public LibraryComponent() {
        this("com.azkar.i18n.home");
    }

    @SuppressWarnings("UnusedMethod")
    @FXML
    private void initialize() {
        wireChildComponents();
        setupControls();
        loadDataAsync();
    }

    private void wireChildComponents() {
        filterCoordinator = new LibraryFilterCoordinator(
                bundle,
                filteringService,
                filterToolbarComponent,
                metaRowComponent,
                browsePaneComponent);

        browseController = new LibraryBrowseController(
                bundle,
                remembrancePresenter,
                () -> selectedRemembrance,
                this::toggleFavorite,
                this::openCollectionDialog,
                this::showReadDialog,
                this::setSelectedRemembrance,
                () -> setSelectedRemembrance(null),
                filterToolbarComponent,
                browsePaneComponent);

        detailPaneComponent.setOnReadAction(this::onDetailReadClicked);
        detailPaneComponent.setOnCollectionAction(this::onDetailCollectionClicked);
        detailPaneComponent.setOnFavoriteAction(this::onDetailFavoriteClicked);
        detailController = new LibraryDetailController(bundle, remembrancePresenter, detailPaneComponent);

        LibraryReminderPanelComponent reminderPanel = detailPaneComponent.getReminderPanel();
        reminderPlannerController = new LibraryReminderPlannerController(
                bundle,
                remembrancePresenter::primaryCategory,
                remembrancePresenter::localizedPrimaryText,
                () -> allRemembrances,
                () -> selectedRemembrance,
                (remembrance, tagName) -> runContextMutation(
                        context -> addTagToRemembrance(context, remembrance, tagName), this::refreshAndRender),
                this::showNotificationPopup,
                reminderPanel);

        headerComponent.setOnRefreshAction(this::onRefreshClicked);
    }

    private void setupControls() {
        filterCoordinator.initialize(this::applyFilters);

        browseController.initialize();
        detailController.initialize();
        reminderPlannerController.initialize();
    }

    private void loadDataAsync() {
        if (closed) {
            return;
        }

        reminderPlannerController.cancelScheduleAndSetIdle();
        dataCoordinator.loadAsync(
                () -> setLoading(true),
                snapshot -> {
                    allRemembrances = snapshot.remembrances();
                    browseController.updateAllRemembrances(allRemembrances);

                    List<LibraryCollectionOption> options =
                            filterCoordinator.updateCollectionOptions(snapshot.collectionNames());
                    reminderPlannerController.syncReminderCollectionChoices(options);

                    reminderPlannerController.pruneSelections();
                    setLoading(false);
                    applyFilters();
                    updateInfoLine(snapshot.refreshedAt());
                },
                failure -> {
                    allRemembrances = List.of();
                    filteredRemembrances = List.of();
                    browseController.updateAllRemembrances(allRemembrances);

                    List<LibraryCollectionOption> options =
                            filterCoordinator.updateCollectionOptions(defaultCollectionOptions());
                    reminderPlannerController.syncReminderCollectionChoices(options);

                    setLoading(false);
                    applyFilters();
                    infoLine.setText(bundle.getString("libraryLoadFailed"));
                });
    }

    private List<String> defaultCollectionOptions() {
        return List.of();
    }

    private void applyFilters() {
        if (loading) {
            return;
        }

        filteredRemembrances = filterCoordinator.apply(allRemembrances);

        browseController.updateFilteredRemembrances(filteredRemembrances);
        browseController.updateEmptyState(!loading && filteredRemembrances.isEmpty());
        browseController.restorePreferredSelection();
    }

    private void setSelectedRemembrance(Remembrance remembrance) {
        selectedRemembrance = remembrance;
        detailController.showRemembrance(remembrance);
        browseController.refreshSelectionVisualState();
    }

    private void withSelectedRemembranceId(LongConsumer action) {
        if (selectedRemembrance == null) {
            return;
        }
        selectedRemembrance.getId().ifPresent(action::accept);
    }

    @FXML
    private void onDetailFavoriteClicked() {
        withSelectedRemembranceId(this::toggleFavorite);
    }

    @FXML
    private void onDetailCollectionClicked() {
        withSelectedRemembranceId(this::openCollectionDialog);
    }

    @FXML
    private void onDetailReadClicked() {
        withSelectedRemembranceId(this::showReadDialog);
    }

    private void setLoading(boolean shouldLoad) {
        loading = shouldLoad;
        browseController.setLoading(shouldLoad);

        if (shouldLoad) {
            infoLine.setText(bundle.getString("libraryLoading"));
        }
    }

    private void toggleFavorite(long remembranceId) {
        Remembrance remembrance = findRemembranceById(remembranceId);
        if (remembrance == null) {
            return;
        }

        browseController.setPreferredSelectionId(remembranceId);
        boolean currentlyFavorite = remembrance.isFavorite();
        runContextMutation(
                context -> {
                    if (currentlyFavorite) {
                        context.remembranceService().unmarkFavorite(remembranceId);
                    } else {
                        context.remembranceService().markFavorite(remembranceId);
                    }
                },
                () -> {
                    reminderPlannerController.refreshSelectionsForFavoriteChange(remembranceId, !currentlyFavorite);
                    refreshAndRender();
                });
    }

    private void openCollectionDialog(long remembranceId) {
        Remembrance remembrance = findRemembranceById(remembranceId);
        if (remembrance == null) {
            return;
        }

        Optional<LibraryCollectionDialogResult> dialogResult =
                collectionDialogSupport.showDialog(filterCoordinator.collectionOptions(), this::styleDialog);
        if (dialogResult.isEmpty()) {
            return;
        }

        browseController.setPreferredSelectionId(remembranceId);
        LibraryCollectionDialogResult selection = dialogResult.get();

        reminderPlannerController.applyLegacyDialogSelection(
                remembrance, selection.addToReminderCollection(), selection.addToReminderFavorites());

        if (selection.hasCollectionToApply()) {
            String collectionToApply = selection.collectionToApply();
            runContextMutation(
                    context -> addTagToRemembrance(context, remembrance, collectionToApply), this::refreshAndRender);
            return;
        }

        applyFilters();
    }

    private void addTagToRemembrance(DomainServiceContext context, Remembrance remembrance, String newTagName) {
        dataService.addTagToRemembrance(context, remembrance, newTagName);
    }

    private void showReadDialog(long remembranceId) {
        Remembrance remembrance = findRemembranceById(remembranceId);
        if (remembrance == null) {
            return;
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(bundle.getString("libraryReadDialogTitle"));
        alert.setHeaderText(remembrancePresenter.primaryCategory(remembrance));
        styleDialog(alert);

        String ar = remembrancePresenter.localizedPrimaryText(remembrance);
        String en = remembrancePresenter.localizedSecondaryText(remembrance);
        String source = remembrance.getSource().orElse(bundle.getString("librarySourceUnknown"));
        alert.setContentText(ar + "\n\n" + en + "\n\n" + bundle.getString("librarySourceLabel") + " " + source);

        alert.getDialogPane().setMinHeight(220);
        alert.showAndWait();
    }

    private void styleDialog(Dialog<?> dialog) {
        overlaySupport.styleDialog(dialog, getScene());
    }

    @FXML
    private void onRefreshClicked() {
        refreshAndRender();
    }

    private void showNotificationPopup(String title, String message, NotificationPriority priority) {
        overlaySupport.showNotificationPopup(title, message, priority, getScene());
    }

    private void updateInfoLine(Instant refreshedAt) {
        String timestamp = infoDateFormatter.format(refreshedAt.atZone(ZoneId.systemDefault()));
        infoLine.setText(bundle.getString("libraryInfoRefreshedAt") + " " + timestamp);
    }

    private void refreshAndRender() {
        loadDataAsync();
    }

    private void runContextMutation(LibraryDataCoordinator.ContextOperation operation, Runnable onSuccess) {
        dataCoordinator.runMutation(
                operation,
                () -> setLoading(true),
                onSuccess,
                failure -> {
                    setLoading(false);
                    infoLine.setText(bundle.getString("libraryLoadFailed"));
                });
    }

    private Remembrance findRemembranceById(long remembranceId) {
        return browseController.findById(remembranceId);
    }

    public void shutdown() {
        if (closed) {
            return;
        }
        closed = true;
        filterCoordinator.stop();
        reminderPlannerController.shutdown();
        dataCoordinator.shutdownNow();
        popupDismissExecutor.shutdownNow();
    }
}
