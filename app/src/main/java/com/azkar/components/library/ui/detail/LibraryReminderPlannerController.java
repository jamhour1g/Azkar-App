package com.azkar.components.library.ui.detail;

import com.azkar.components.library.model.LibraryCollectionOption;
import com.azkar.components.library.model.NotificationCadence;
import com.azkar.components.library.model.NotificationPriority;
import com.azkar.components.library.model.ReminderPlan;
import com.azkar.components.library.model.ReminderSelectionEntry;
import com.azkar.components.library.model.ReminderSelectionMode;
import com.azkar.components.library.model.ReminderTargetOption;
import com.azkar.components.library.model.ScheduledReminderItem;
import com.azkar.components.library.service.ReminderScheduler;
import com.azkar.domain.model.Remembrance;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

public final class LibraryReminderPlannerController {

    private final ResourceBundle bundle;
    private final Function<Remembrance, String> primaryCategoryProvider;
    private final Function<Remembrance, String> primaryTextProvider;
    private final Supplier<List<Remembrance>> allRemembrancesSupplier;
    private final Supplier<Remembrance> selectedRemembranceSupplier;
    private final BiConsumer<Remembrance, String> addTagToRemembranceAction;
    private final ReminderScheduler.NotificationNotifier notificationNotifier;
    private final ReminderScheduler reminderScheduler = new ReminderScheduler();
    private final List<ReminderSelectionEntry> collectionSelections = new ArrayList<>();
    private final List<ReminderSelectionEntry> customSelections = new ArrayList<>();
    private ReminderSelectionEntry singleSelection;
    private final ChoiceBox<ReminderSelectionMode> reminderModeChoice;
    private final javafx.scene.layout.VBox singleModeBox;
    private final ChoiceBox<ReminderTargetOption> singleRemembranceChoice;
    private final ChoiceBox<NotificationPriority> singlePriorityChoice;
    private final Label singleSelectionLabel;
    private final javafx.scene.layout.VBox collectionsModeBox;
    private final ChoiceBox<LibraryCollectionOption> reminderCollectionChoice;
    private final ChoiceBox<NotificationPriority> collectionPriorityChoice;
    private final FlowPane reminderCollectionChipsPane;
    private final javafx.scene.layout.VBox customModeBox;
    private final TextField customCollectionField;
    private final ChoiceBox<NotificationPriority> customPriorityChoice;
    private final FlowPane reminderCustomChipsPane;
    private final Label reminderCollectionCountLabel;
    private final Label reminderFavoritesCountLabel;
    private final Label schedulerStatusLabel;
    private final ChoiceBox<NotificationCadence> cadenceChoice;
    private final Spinner<Integer> notificationsPerCycleSpinner;
    private final ToggleButton randomOrderToggle;
    private final Label schedulerPreviewLabel;

    public LibraryReminderPlannerController(
            ResourceBundle bundle,
            Function<Remembrance, String> primaryCategoryProvider,
            Function<Remembrance, String> primaryTextProvider,
            Supplier<List<Remembrance>> allRemembrancesSupplier,
            Supplier<Remembrance> selectedRemembranceSupplier,
            BiConsumer<Remembrance, String> addTagToRemembranceAction,
            ReminderScheduler.NotificationNotifier notificationNotifier,
            LibraryReminderPanelComponent reminderPanel) {
        this.bundle = bundle;
        this.primaryCategoryProvider = primaryCategoryProvider;
        this.primaryTextProvider = primaryTextProvider;
        this.allRemembrancesSupplier = allRemembrancesSupplier;
        this.selectedRemembranceSupplier = selectedRemembranceSupplier;
        this.addTagToRemembranceAction = addTagToRemembranceAction;
        this.notificationNotifier = notificationNotifier;

        reminderModeChoice = reminderPanel.getModeChoice();
        singleModeBox = reminderPanel.getSingleModeBox();
        singleRemembranceChoice = reminderPanel.getSingleRemembranceChoice();
        singlePriorityChoice = reminderPanel.getSinglePriorityChoice();
        singleSelectionLabel = reminderPanel.getSingleSelectionLabel();
        collectionsModeBox = reminderPanel.getCollectionsModeBox();
        reminderCollectionChoice = reminderPanel.getCollectionChoice();
        collectionPriorityChoice = reminderPanel.getCollectionPriorityChoice();
        reminderCollectionChipsPane = reminderPanel.getCollectionChipsPane();
        customModeBox = reminderPanel.getCustomModeBox();
        customCollectionField = reminderPanel.getCustomCollectionField();
        customPriorityChoice = reminderPanel.getCustomPriorityChoice();
        reminderCustomChipsPane = reminderPanel.getCustomChipsPane();
        cadenceChoice = reminderPanel.getCadenceChoice();
        notificationsPerCycleSpinner = reminderPanel.getNotificationsPerCycleSpinner();
        randomOrderToggle = reminderPanel.getRandomOrderToggle();
        schedulerStatusLabel = reminderPanel.getSchedulerStatusLabel();
        reminderCollectionCountLabel = reminderPanel.getReminderCollectionCountLabel();
        reminderFavoritesCountLabel = reminderPanel.getReminderFavoritesCountLabel();
        schedulerPreviewLabel = reminderPanel.getSchedulerPreviewLabel();

        reminderPanel.setOnScheduleAction(this::onScheduleNotificationsClicked);
        reminderPanel.setOnStopAction(this::onStopNotificationsClicked);
        reminderPanel.setOnApplySingleAction(this::onApplySingleReminderClicked);
        reminderPanel.setOnAddCollectionAction(this::onAddCollectionReminderClicked);
        reminderPanel.setOnAddCustomCollectionAction(this::onAddCustomReminderClicked);
    }

    public void initialize() {
        setupReminderPlannerControls();
        setupDeliveryControls();

        schedulerStatusLabel.setText(bundle.getString("librarySchedulerStatusIdle"));
        randomOrderToggle.setText(bundle.getString("libraryRandomOrder"));

        refreshReminderTargetOptions();
        refreshReminderSelectionChips();
        updateReminderCounters();
        updateSchedulerPreview();
    }

    public void syncReminderCollectionChoices(List<LibraryCollectionOption> options) {
        LibraryCollectionOption previous = reminderCollectionChoice.getValue();
        List<LibraryCollectionOption> plannerOptions =
                options.stream().filter(option -> !option.isAllCollections()).toList();
        reminderCollectionChoice.getItems().setAll(plannerOptions);

        if (previous != null) {
            Optional<LibraryCollectionOption> match = plannerOptions.stream()
                    .filter(option -> option.key().equals(previous.key()))
                    .findFirst();
            if (match.isPresent()) {
                reminderCollectionChoice.getSelectionModel().select(match.get());
                return;
            }
        }

        if (!plannerOptions.isEmpty()) {
            reminderCollectionChoice.getSelectionModel().selectFirst();
        }
    }

    public void pruneSelections() {
        List<Remembrance> allRemembrances = allRemembrancesSupplier.get();
        singleSelection = LibraryReminderSelectionSupport.pruneSelections(
                singleSelection,
                collectionSelections,
                customSelections,
                allRemembrances,
                reminderCollectionChoice.getItems(),
                this::toReminderTargetLabel);

        updateSingleSelectionLabel();
        refreshReminderSelectionChips();
        refreshReminderTargetOptions();
        updateReminderCounters();
        updateSchedulerPreview();
    }

    public void refreshSelectionsForFavoriteChange(long remembranceId, boolean nowFavorite) {
        List<Remembrance> allRemembrances = allRemembrancesSupplier.get();
        singleSelection = LibraryReminderSelectionSupport.refreshSelectionsForFavoriteChange(
                singleSelection, collectionSelections, remembranceId, nowFavorite, allRemembrances);

        refreshReminderSelectionChips();
        updateReminderCounters();
        updateSchedulerPreview();
    }

    public void applyLegacyDialogSelection(
            Remembrance remembrance, boolean reminderCollectionSelected, boolean reminderFavoritesSelected) {
        NotificationPriority fallbackPriority = NotificationPriority.fallback(collectionPriorityChoice.getValue());
        if (singleSelection == null) {
            singleSelection =
                    ReminderSelectionEntry.single(remembrance, toReminderTargetLabel(remembrance), fallbackPriority);
            updateSingleSelectionLabel();
        }

        if (reminderCollectionSelected || reminderFavoritesSelected) {
            schedulerStatusLabel.setText(bundle.getString("libraryReminderStatusLegacyDialogSelection"));
        }

        updateReminderCounters();
        updateSchedulerPreview();
        refreshReminderSelectionChips();
    }

    public void cancelScheduleAndSetIdle() {
        reminderScheduler.cancelCurrentSchedule();
        schedulerStatusLabel.setText(bundle.getString("librarySchedulerStatusIdle"));
    }

    public void shutdown() {
        reminderScheduler.close();
    }

    private void setupReminderPlannerControls() {
        reminderModeChoice.getItems().setAll(ReminderSelectionMode.values());
        reminderModeChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(ReminderSelectionMode mode) {
                return mode == null ? "" : bundle.getString(mode.labelKey());
            }

            @Override
            public ReminderSelectionMode fromString(String value) {
                return Stream.of(ReminderSelectionMode.values())
                        .filter(option -> bundle.getString(option.labelKey()).equals(value))
                        .findFirst()
                        .orElse(ReminderSelectionMode.SINGLE_ITEM);
            }
        });
        reminderModeChoice
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldMode, selectedMode) -> {
                    applyReminderModeVisibility(selectedMode);
                    updateSchedulerPreview();
                });

        StringConverter<NotificationPriority> priorityConverter = new StringConverter<>() {
            @Override
            public String toString(NotificationPriority priority) {
                return priority == null ? "" : bundle.getString(priority.labelKey());
            }

            @Override
            public NotificationPriority fromString(String value) {
                return Stream.of(NotificationPriority.values())
                        .filter(priority ->
                                bundle.getString(priority.labelKey()).equals(value))
                        .findFirst()
                        .orElse(NotificationPriority.MEDIUM);
            }
        };

        singlePriorityChoice.getItems().setAll(NotificationPriority.values());
        singlePriorityChoice.setConverter(priorityConverter);
        singlePriorityChoice.getSelectionModel().select(NotificationPriority.MEDIUM);

        collectionPriorityChoice.getItems().setAll(NotificationPriority.values());
        collectionPriorityChoice.setConverter(priorityConverter);
        collectionPriorityChoice.getSelectionModel().select(NotificationPriority.MEDIUM);

        customPriorityChoice.getItems().setAll(NotificationPriority.values());
        customPriorityChoice.setConverter(priorityConverter);
        customPriorityChoice.getSelectionModel().select(NotificationPriority.MEDIUM);

        reminderCollectionChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(LibraryCollectionOption option) {
                return option == null ? "" : option.label();
            }

            @Override
            public LibraryCollectionOption fromString(String value) {
                return reminderCollectionChoice.getItems().stream()
                        .filter(option -> option.label().equals(value))
                        .findFirst()
                        .orElse(null);
            }
        });

        reminderModeChoice.getSelectionModel().select(ReminderSelectionMode.SINGLE_ITEM);
        applyReminderModeVisibility(reminderModeChoice.getValue());
        updateSingleSelectionLabel();
    }

    private void setupDeliveryControls() {
        cadenceChoice.getItems().setAll(NotificationCadence.values());
        cadenceChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(NotificationCadence cadence) {
                return cadence == null ? "" : bundle.getString(cadence.labelKey());
            }

            @Override
            public NotificationCadence fromString(String value) {
                return Stream.of(NotificationCadence.values())
                        .filter(option -> bundle.getString(option.labelKey()).equals(value))
                        .findFirst()
                        .orElse(null);
            }
        });
        cadenceChoice.getSelectionModel().select(NotificationCadence.EVERY_2_HOURS);
        cadenceChoice
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldCadence, newCadence) -> updateSchedulerPreview());

        notificationsPerCycleSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 5, 1));
        notificationsPerCycleSpinner
                .valueProperty()
                .addListener((observable, oldValue, newValue) -> updateSchedulerPreview());

        randomOrderToggle.selectedProperty().addListener((observable, wasSelected, selected) -> {
            randomOrderToggle.setText(
                    selected ? bundle.getString("libraryRandomOrderOn") : bundle.getString("libraryRandomOrder"));
            updateSchedulerPreview();
        });
    }

    private void applyReminderModeVisibility(ReminderSelectionMode mode) {
        ReminderSelectionMode resolvedMode = mode == null ? ReminderSelectionMode.SINGLE_ITEM : mode;
        boolean showSingle = resolvedMode == ReminderSelectionMode.SINGLE_ITEM;
        boolean showCollections = resolvedMode == ReminderSelectionMode.COLLECTIONS;
        boolean showCustom = resolvedMode == ReminderSelectionMode.CUSTOM_COLLECTIONS;

        singleModeBox.setManaged(showSingle);
        singleModeBox.setVisible(showSingle);

        collectionsModeBox.setManaged(showCollections);
        collectionsModeBox.setVisible(showCollections);

        customModeBox.setManaged(showCustom);
        customModeBox.setVisible(showCustom);
    }

    private void refreshReminderTargetOptions() {
        List<Remembrance> allRemembrances = allRemembrancesSupplier.get();
        final long selectedId =
                singleSelection != null && !singleSelection.remembrances().isEmpty()
                        ? singleSelection.remembrances().getFirst().getId().orElse(-1L)
                        : -1L;

        List<ReminderTargetOption> options = allRemembrances.stream()
                .filter(remembrance -> remembrance.getId().isPresent())
                .map(remembrance -> new ReminderTargetOption(
                        remembrance.getId().orElse(-1L), toReminderTargetLabel(remembrance), remembrance))
                .toList();
        singleRemembranceChoice.getItems().setAll(options);

        if (selectedId > 0) {
            Optional<ReminderTargetOption> selected =
                    options.stream().filter(option -> option.id() == selectedId).findFirst();
            if (selected.isPresent()) {
                singleRemembranceChoice.getSelectionModel().select(selected.get());
                updateSingleSelectionLabel();
                return;
            }
        }

        if (!options.isEmpty()) {
            singleRemembranceChoice.getSelectionModel().selectFirst();
            return;
        }

        singleRemembranceChoice.getSelectionModel().clearSelection();
        updateSingleSelectionLabel();
    }

    private String toReminderTargetLabel(Remembrance remembrance) {
        String preview =
                Optional.ofNullable(primaryTextProvider.apply(remembrance)).orElse("");
        String compact = preview.replace('\n', ' ').trim();
        if (compact.length() > 44) {
            compact = compact.substring(0, 44) + "...";
        }
        if (compact.isBlank()) {
            compact = bundle.getString("libraryMissingText");
        }
        return primaryCategoryProvider.apply(remembrance) + " · " + compact;
    }

    private void updateSingleSelectionLabel() {
        if (singleSelection == null) {
            singleSelectionLabel.setText(bundle.getString("libraryReminderSingleNotSet"));
            return;
        }

        String priorityLabel = bundle.getString(singleSelection.priority().labelKey());
        singleSelectionLabel.setText(bundle.getString("libraryReminderSingleSelectedPrefix")
                + " "
                + singleSelection.label()
                + " ("
                + priorityLabel
                + ")");
    }


    private void onApplySingleReminderClicked() {
        ReminderTargetOption target = singleRemembranceChoice.getValue();
        if (target == null) {
            schedulerStatusLabel.setText(bundle.getString("librarySchedulerStatusNoSelection"));
            return;
        }

        NotificationPriority priority = NotificationPriority.fallback(singlePriorityChoice.getValue());
        singleSelection = ReminderSelectionEntry.single(target.remembrance(), target.label(), priority);

        updateSingleSelectionLabel();
        updateReminderCounters();
        updateSchedulerPreview();
        schedulerStatusLabel.setText(bundle.getString("libraryReminderStatusSelectionUpdated"));
    }

    private void onAddCollectionReminderClicked() {
        LibraryCollectionOption selectedCollection = reminderCollectionChoice.getValue();
        if (selectedCollection == null) {
            schedulerStatusLabel.setText(bundle.getString("librarySchedulerStatusNoSelection"));
            return;
        }

        List<Remembrance> remembrances = LibraryReminderSelectionSupport.resolveRemembrancesForCollection(
                selectedCollection, allRemembrancesSupplier.get());
        if (remembrances.isEmpty()) {
            schedulerStatusLabel.setText(bundle.getString("libraryReminderStatusCollectionEmpty"));
            return;
        }

        NotificationPriority priority = NotificationPriority.fallback(collectionPriorityChoice.getValue());
        String sourceTag = selectedCollection.isTagCollection() ? selectedCollection.tagNameKey() : "";
        ReminderSelectionEntry entry = ReminderSelectionEntry.collection(
                selectedCollection.key(), selectedCollection.label(), priority, remembrances, sourceTag);
        upsertSelection(collectionSelections, entry);

        refreshReminderSelectionChips();
        updateReminderCounters();
        updateSchedulerPreview();
        schedulerStatusLabel.setText(bundle.getString("libraryReminderStatusSelectionUpdated"));
    }

    private void onAddCustomReminderClicked() {
        String customCollectionName =
                Optional.ofNullable(customCollectionField.getText()).orElse("").trim();
        if (customCollectionName.isBlank()) {
            schedulerStatusLabel.setText(bundle.getString("libraryReminderStatusCustomNameRequired"));
            return;
        }

        List<Remembrance> allRemembrances = allRemembrancesSupplier.get();
        NotificationPriority priority = NotificationPriority.fallback(customPriorityChoice.getValue());
        List<Remembrance> remembrances = allRemembrances.stream()
                .filter(item ->
                        item.getTags().stream().anyMatch(tag -> tag.getName().equalsIgnoreCase(customCollectionName)))
                .toList();

        if (remembrances.isEmpty()) {
            Remembrance selectedRemembrance = selectedRemembranceSupplier.get();
            if (selectedRemembrance == null || selectedRemembrance.getId().isEmpty()) {
                schedulerStatusLabel.setText(bundle.getString("libraryReminderStatusCustomNeedsSelection"));
                return;
            }

            Remembrance seedRemembrance = selectedRemembrance;
            remembrances = List.of(seedRemembrance);
            addTagToRemembranceAction.accept(seedRemembrance, customCollectionName);
        }

        ReminderSelectionEntry entry =
                ReminderSelectionEntry.custom(customCollectionName, customCollectionName, priority, remembrances);
        upsertSelection(customSelections, entry);

        customCollectionField.clear();
        refreshReminderSelectionChips();
        updateReminderCounters();
        updateSchedulerPreview();
        schedulerStatusLabel.setText(bundle.getString("libraryReminderStatusSelectionUpdated"));
    }

    private void upsertSelection(List<ReminderSelectionEntry> selections, ReminderSelectionEntry candidate) {
        for (int index = 0; index < selections.size(); index++) {
            if (selections.get(index).id().equals(candidate.id())) {
                selections.set(index, candidate);
                return;
            }
        }
        selections.add(candidate);
    }

    private void refreshReminderSelectionChips() {
        renderSelectionChips(reminderCollectionChipsPane, collectionSelections);
        renderSelectionChips(reminderCustomChipsPane, customSelections);
    }

    private void renderSelectionChips(FlowPane targetPane, List<ReminderSelectionEntry> entries) {
        targetPane.getChildren().clear();
        if (entries.isEmpty()) {
            Label emptyLabel = new Label(bundle.getString("libraryReminderNoSelections"));
            emptyLabel.getStyleClass().add("library-reminder-stat");
            targetPane.getChildren().add(emptyLabel);
            return;
        }

        for (ReminderSelectionEntry entry : List.copyOf(entries)) {
            HBox chip = buildSelectionChip(entry, () -> {
                entries.removeIf(item -> item.id().equals(entry.id()));
                refreshReminderSelectionChips();
                updateReminderCounters();
                updateSchedulerPreview();
            });
            targetPane.getChildren().add(chip);
        }
    }

    private HBox buildSelectionChip(ReminderSelectionEntry entry, Runnable onRemove) {
        Label selectionLabel = new Label(entry.label());
        selectionLabel.getStyleClass().add("library-reminder-pick-label");

        NotificationPriority priority = NotificationPriority.fallback(entry.priority());
        Label priorityLabel = new Label(bundle.getString(priority.labelKey()));
        priorityLabel.getStyleClass().addAll("library-reminder-priority-pill", priorityPillStyleClass(priority));

        Button removeButton = new Button("x");
        removeButton.setMnemonicParsing(false);
        removeButton.getStyleClass().add("library-reminder-chip-remove");
        removeButton.setOnAction(event -> onRemove.run());

        HBox chip = new HBox(6, selectionLabel, priorityLabel, removeButton);
        chip.getStyleClass().add("library-reminder-pick-chip");
        return chip;
    }

    private String priorityPillStyleClass(NotificationPriority priority) {
        return switch (NotificationPriority.fallback(priority)) {
            case HIGH -> "library-reminder-priority-pill-high";
            case MEDIUM -> "library-reminder-priority-pill-medium";
            case LOW -> "library-reminder-priority-pill-low";
        };
    }

    private void onScheduleNotificationsClicked() {
        List<ScheduledReminderItem> chosenReminders = LibraryReminderSelectionSupport.gatherScheduledReminderItems(
                singleSelection, collectionSelections, customSelections);
        if (chosenReminders.isEmpty()) {
            schedulerStatusLabel.setText(bundle.getString("librarySchedulerStatusNoSelection"));
            return;
        }

        ReminderPlan plan = new ReminderPlan(
                bundle.getString("libraryReminderPlanTitle"),
                chosenReminders,
                cadenceChoice.getValue(),
                notificationsPerCycleSpinner.getValue(),
                randomOrderToggle.isSelected());

        reminderScheduler.schedule(plan, notificationNotifier);
        schedulerStatusLabel.setText(bundle.getString("librarySchedulerStatusRunning"));
    }

    private void onStopNotificationsClicked() {
        reminderScheduler.cancelCurrentSchedule();
        schedulerStatusLabel.setText(bundle.getString("librarySchedulerStatusStopped"));
    }


    private void updateReminderCounters() {
        int singleCount = singleSelection == null ? 0 : 1;
        reminderCollectionCountLabel.setText(
                bundle.getString("libraryReminderCollectionCount") + " " + (singleCount + collectionSelections.size()));
        reminderFavoritesCountLabel.setText(
                bundle.getString("libraryReminderFavoritesCount") + " " + customSelections.size());
    }

    private void updateSchedulerPreview() {
        NotificationCadence cadence = cadenceChoice.getValue();
        if (cadence == null) {
            schedulerPreviewLabel.setText(bundle.getString("librarySchedulerPreviewEmpty"));
            return;
        }

        int perCycle =
                Optional.ofNullable(notificationsPerCycleSpinner.getValue()).orElse(1);
        String order = randomOrderToggle.isSelected()
                ? bundle.getString("libraryRandomOrderOn")
                : bundle.getString("libraryRandomOrder");
        String cadenceLabel = cadenceChoice.getConverter() != null
                ? cadenceChoice.getConverter().toString(cadence)
                : bundle.getString(cadence.labelKey());
        schedulerPreviewLabel.setText(bundle.getString("librarySchedulerPreviewPrefix") + " " + perCycle + " · "
                + cadenceLabel + " · " + order);
    }

    // Selection and scheduling helpers live in LibraryReminderSelectionSupport.
}
