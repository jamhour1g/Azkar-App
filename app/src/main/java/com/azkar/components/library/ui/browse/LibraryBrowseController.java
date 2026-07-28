package com.azkar.components.library.ui.browse;

import com.azkar.components.library.model.LibraryRemembranceRow;
import com.azkar.components.library.service.LibraryRemembrancePresenter;
import com.azkar.components.library.ui.card.RemembranceCardComponent;
import com.azkar.components.library.ui.state.LibraryEmptyStateComponent;
import com.azkar.components.library.ui.state.LibraryLoadingStateComponent;
import com.azkar.components.library.ui.toolbar.LibraryFilterToolbarComponent;
import com.azkar.components.library.util.LibraryRemembranceLookup;
import com.azkar.domain.model.Remembrance;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.javafx.FontIcon;

public final class LibraryBrowseController {

    public static final int GRID_PAGE_SIZE = 18;

    private final ResourceBundle bundle;
    private final LibraryRemembrancePresenter remembrancePresenter;
    private final Supplier<Remembrance> selectedRemembranceSupplier;
    private final LongConsumer favoriteAction;
    private final LongConsumer collectionAction;
    private final LongConsumer readAction;
    private final Consumer<Remembrance> selectedRemembranceConsumer;
    private final Runnable emptySelectionAction;

    private final ToggleGroup viewModeGroup;
    private final ToggleButton listViewToggle;
    private final ToggleButton gridViewToggle;
    private final TableView<LibraryRemembranceRow> libraryTable;
    private final TableColumn<LibraryRemembranceRow, String> categoryColumn;
    private final TableColumn<LibraryRemembranceRow, String> arabicColumn;
    private final TableColumn<LibraryRemembranceRow, String> englishColumn;
    private final TableColumn<LibraryRemembranceRow, String> sourceColumn;
    private final TableColumn<LibraryRemembranceRow, LibraryRemembranceRow> actionsColumn;
    private final LibraryGridPaneComponent gridContainer;
    private final FlowPane cardsFlowPane;
    private final ScrollPane gridScrollPane;
    private final Button gridPrevButton;
    private final Button gridNextButton;
    private final Label gridPageLabel;
    private final LibraryLoadingStateComponent loadingState;
    private final LibraryEmptyStateComponent emptyState;

    private List<Remembrance> allRemembrances = List.of();
    private List<Remembrance> filteredRemembrances = List.of();
    private Long preferredSelectionId;
    private int currentGridPage;
    private boolean gridMode;
    private boolean loading;

    public LibraryBrowseController(
            ResourceBundle bundle,
            LibraryRemembrancePresenter remembrancePresenter,
            Supplier<Remembrance> selectedRemembranceSupplier,
            LongConsumer favoriteAction,
            LongConsumer collectionAction,
            LongConsumer readAction,
            Consumer<Remembrance> selectedRemembranceConsumer,
            Runnable emptySelectionAction,
            LibraryFilterToolbarComponent filterToolbarComponent,
            LibraryBrowsePaneComponent browsePaneComponent) {
        this.bundle = bundle;
        this.remembrancePresenter = remembrancePresenter;
        this.selectedRemembranceSupplier = selectedRemembranceSupplier;
        this.favoriteAction = favoriteAction;
        this.collectionAction = collectionAction;
        this.readAction = readAction;
        this.selectedRemembranceConsumer = selectedRemembranceConsumer;
        this.emptySelectionAction = emptySelectionAction;

        viewModeGroup = filterToolbarComponent.getViewModeGroup();
        listViewToggle = filterToolbarComponent.getListViewToggle();
        gridViewToggle = filterToolbarComponent.getGridViewToggle();

        LibraryTableComponent tableComponent = browsePaneComponent.getTableComponent();
        libraryTable = tableComponent;
        categoryColumn = tableComponent.getCategoryColumn();
        arabicColumn = tableComponent.getArabicColumn();
        englishColumn = tableComponent.getEnglishColumn();
        sourceColumn = tableComponent.getSourceColumn();
        actionsColumn = tableComponent.getActionsColumn();

        gridContainer = browsePaneComponent.getGridPaneComponent();
        cardsFlowPane = gridContainer.getCardsFlowPane();
        gridScrollPane = gridContainer.getGridScrollPane();
        gridPrevButton = gridContainer.getGridPrevButton();
        gridNextButton = gridContainer.getGridNextButton();
        gridPageLabel = gridContainer.getGridPageLabel();

        loadingState = browsePaneComponent.getLoadingStateComponent();
        emptyState = browsePaneComponent.getEmptyStateComponent();
    }

    public void initialize() {
        setupViewModeControls();
        setupTable();
        setupGridControls();
        setGridMode(false);
    }

    public void updateAllRemembrances(List<Remembrance> remembrances) {
        allRemembrances = remembrances == null ? List.of() : remembrances;
    }

    public void updateFilteredRemembrances(List<Remembrance> remembrances) {
        filteredRemembrances = remembrances == null ? List.of() : remembrances;
        renderTable(filteredRemembrances);
        clampAndRenderGrid();
    }

    public void setLoading(boolean shouldLoad) {
        loading = shouldLoad;
        loadingState.setManaged(shouldLoad);
        loadingState.setVisible(shouldLoad);
        loadingState.toFront();

        libraryTable.setDisable(shouldLoad);
        gridContainer.setDisable(shouldLoad);

        if (!shouldLoad) {
            updateEmptyState(filteredRemembrances.isEmpty());
        }
    }

    public void updateEmptyState(boolean show) {
        emptyState.setManaged(show);
        emptyState.setVisible(show);

        if (show) {
            emptyState.toFront();
        } else if (gridMode) {
            gridContainer.toFront();
        } else {
            libraryTable.toFront();
        }

        if (loading) {
            loadingState.toFront();
        }
    }

    public void restorePreferredSelection() {
        if (preferredSelectionId == null) {
            if (!filteredRemembrances.isEmpty() && selectedRemembranceSupplier.get() == null) {
                selectedRemembranceConsumer.accept(filteredRemembrances.getFirst());
            }
            return;
        }

        long targetId = preferredSelectionId;
        preferredSelectionId = null;

        Remembrance match = findById(targetId);
        if (match != null) {
            selectedRemembranceConsumer.accept(match);
            for (int index = 0; index < libraryTable.getItems().size(); index++) {
                if (libraryTable.getItems().get(index).id() == targetId) {
                    libraryTable.getSelectionModel().select(index);
                    break;
                }
            }
            return;
        }

        if (!filteredRemembrances.isEmpty()) {
            libraryTable.getSelectionModel().selectFirst();
        }
    }

    public void setPreferredSelectionId(Long remembranceId) {
        preferredSelectionId = remembranceId;
    }

    public void refreshSelectionVisualState() {
        if (gridMode) {
            renderGridPage();
        }
    }

    public Remembrance findById(long remembranceId) {
        return LibraryRemembranceLookup.findById(allRemembrances, remembranceId);
    }

    private void setupViewModeControls() {
        viewModeGroup.selectedToggleProperty().addListener((observable, oldToggle, selectedToggle) -> {
            if (selectedToggle == null) {
                viewModeGroup.selectToggle(listViewToggle);
                return;
            }

            if (selectedToggle == gridViewToggle) {
                setGridMode(true);
                return;
            }
            setGridMode(false);
        });
        viewModeGroup.selectToggle(listViewToggle);
    }

    private void setupTable() {
        libraryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        categoryColumn.setCellValueFactory(
                item -> new ReadOnlyStringWrapper(item.getValue().category()));
        arabicColumn.setCellValueFactory(
                item -> new ReadOnlyStringWrapper(item.getValue().primaryPreview()));
        englishColumn.setCellValueFactory(
                item -> new ReadOnlyStringWrapper(item.getValue().secondaryPreview()));
        sourceColumn.setCellValueFactory(
                item -> new ReadOnlyStringWrapper(item.getValue().source()));

        actionsColumn.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        actionsColumn.setCellFactory(
                column -> new LibraryTableActionsCell(bundle, readAction, collectionAction, favoriteAction));

        libraryTable.getSelectionModel().selectedItemProperty().addListener((observable, oldRow, row) -> {
            if (row == null) {
                emptySelectionAction.run();
                return;
            }
            Remembrance remembrance = findById(row.id());
            selectedRemembranceConsumer.accept(remembrance);
        });
    }

    private void setupGridControls() {
        gridContainer.setOnPreviousPageAction(this::onGridPrevClicked);
        gridContainer.setOnNextPageAction(this::onGridNextClicked);

        gridScrollPane
                .viewportBoundsProperty()
                .addListener((observable, oldBounds, newBounds) -> updateGridWrapLength());
        gridScrollPane.widthProperty().addListener((observable, oldWidth, newWidth) -> updateGridWrapLength());
        Platform.runLater(this::updateGridWrapLength);
    }

    private void setGridMode(boolean shouldUseGrid) {
        gridMode = shouldUseGrid;
        gridContainer.setManaged(shouldUseGrid);
        gridContainer.setVisible(shouldUseGrid);

        libraryTable.setManaged(!shouldUseGrid);
        libraryTable.setVisible(!shouldUseGrid);

        if (shouldUseGrid) {
            updateGridWrapLength();
            clampAndRenderGrid();
            gridContainer.toFront();
        } else {
            libraryTable.toFront();
        }

        if (loading) {
            loadingState.toFront();
        } else if (filteredRemembrances.isEmpty()) {
            emptyState.toFront();
        }
    }

    private void renderTable(List<Remembrance> remembrances) {
        Remembrance selectedRemembrance = selectedRemembranceSupplier.get();
        long selectedId =
                selectedRemembrance == null ? -1L : selectedRemembrance.getId().orElse(-1L);
        List<LibraryRemembranceRow> rows =
                remembrances.stream().map(remembrancePresenter::toRow).toList();
        libraryTable.getItems().setAll(rows);

        if (rows.isEmpty()) {
            libraryTable.getSelectionModel().clearSelection();
            emptySelectionAction.run();
            return;
        }

        int indexToSelect = -1;
        if (selectedId > 0) {
            for (int index = 0; index < rows.size(); index++) {
                if (rows.get(index).id() == selectedId) {
                    indexToSelect = index;
                    break;
                }
            }
        }

        if (indexToSelect < 0 && preferredSelectionId != null) {
            for (int index = 0; index < rows.size(); index++) {
                if (rows.get(index).id() == preferredSelectionId) {
                    indexToSelect = index;
                    break;
                }
            }
        }

        if (indexToSelect < 0) {
            indexToSelect = 0;
        }

        libraryTable.getSelectionModel().select(indexToSelect);
    }

    private void clampAndRenderGrid() {
        int pages = totalGridPages();
        if (currentGridPage >= pages) {
            currentGridPage = Math.max(0, pages - 1);
        }
        renderGridPage();
    }

    private void renderGridPage() {
        cardsFlowPane.getChildren().clear();

        if (filteredRemembrances.isEmpty()) {
            gridPrevButton.setDisable(true);
            gridNextButton.setDisable(true);
            gridPageLabel.setText(bundle.getString("libraryGridPage") + " 0/0");
            return;
        }

        int pages = totalGridPages();
        int start = currentGridPage * GRID_PAGE_SIZE;
        int end = Math.min(start + GRID_PAGE_SIZE, filteredRemembrances.size());

        for (int index = start; index < end; index++) {
            Remembrance remembrance = filteredRemembrances.get(index);
            RemembranceCardComponent card = new RemembranceCardComponent();
            bindCard(card, remembrance);
            card.setPrefWidth(360);
            card.setMaxWidth(380);

            Remembrance selectedRemembrance = selectedRemembranceSupplier.get();
            boolean selected = selectedRemembrance != null
                    && selectedRemembrance.getId().isPresent()
                    && remembrance.getId().isPresent()
                    && selectedRemembrance
                            .getId()
                            .get()
                            .equals(remembrance.getId().get());
            if (selected) {
                card.getStyleClass().add("library-card-selected");
            }

            card.setOnMouseClicked(event -> selectedRemembranceConsumer.accept(remembrance));
            cardsFlowPane.getChildren().add(card);
        }

        gridPrevButton.setDisable(currentGridPage <= 0);
        gridNextButton.setDisable(currentGridPage >= pages - 1);
        gridPageLabel.setText(bundle.getString("libraryGridPage") + " " + (currentGridPage + 1) + "/" + pages);
    }

    private void updateGridWrapLength() {
        if (gridScrollPane == null || cardsFlowPane == null) {
            return;
        }

        double viewportWidth = gridScrollPane.getViewportBounds().getWidth();
        if (viewportWidth <= 0) {
            viewportWidth = gridScrollPane.getWidth();
        }

        cardsFlowPane.setPrefWrapLength(Math.max(360, viewportWidth - 14));
    }

    private int totalGridPages() {
        if (filteredRemembrances.isEmpty()) {
            return 0;
        }
        return (filteredRemembrances.size() + GRID_PAGE_SIZE - 1) / GRID_PAGE_SIZE;
    }

    private void onGridPrevClicked() {
        if (currentGridPage <= 0) {
            return;
        }
        currentGridPage--;
        renderGridPage();
    }

    private void onGridNextClicked() {
        int pages = totalGridPages();
        if (currentGridPage >= pages - 1) {
            return;
        }
        currentGridPage++;
        renderGridPage();
    }

    private void bindCard(RemembranceCardComponent card, Remembrance remembrance) {
        long remembranceId = remembrance.getId().orElse(-1L);
        card.setRemembranceId(remembranceId);
        card.setCategory(remembrancePresenter.primaryCategory(remembrance));
        card.setArabicText(remembrancePresenter.localizedPrimaryText(remembrance));
        card.setTranslationText(remembrancePresenter.localizedSecondaryText(remembrance));
        card.setSourceText(remembrance.getSource().orElse(bundle.getString("librarySourceUnknown")));
        card.setFavorite(remembrance.isFavorite());

        card.setOnFavoriteAction(favoriteAction);
        card.setOnAddToCollectionAction(collectionAction);
        card.setOnReadAction(readAction);
    }
}