package com.azkar.components.library.ui.detail;

import com.azkar.components.library.model.LibraryCollectionOption;
import com.azkar.components.library.model.NotificationPriority;
import com.azkar.components.library.model.ReminderSelectionEntry;
import com.azkar.components.library.model.ScheduledReminderItem;
import com.azkar.components.library.util.LibraryKeys;
import com.azkar.domain.model.Remembrance;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

final class LibraryReminderSelectionSupport {

    private static final String COLLECTION_ID_PREFIX = "collection:";

    static ReminderSelectionEntry pruneSelections(
            ReminderSelectionEntry singleSelection,
            List<ReminderSelectionEntry> collectionSelections,
            List<ReminderSelectionEntry> customSelections,
            List<Remembrance> allRemembrances,
            List<LibraryCollectionOption> availablePlannerCollections,
            Function<Remembrance, String> reminderTargetLabelProvider) {
        Map<Long, Remembrance> availableById = new LinkedHashMap<>();
        for (Remembrance remembrance : allRemembrances) {
            if (remembrance.getId().isEmpty()) {
                continue;
            }
            availableById.put(remembrance.getId().orElse(-1L), remembrance);
        }

        ReminderSelectionEntry refreshedSingle = null;
        if (singleSelection != null) {
            List<Remembrance> refreshedSingleRemembrances = remapSelectionRemembrances(singleSelection, availableById);
            if (!refreshedSingleRemembrances.isEmpty()) {
                Remembrance remembrance = refreshedSingleRemembrances.getFirst();
                refreshedSingle = ReminderSelectionEntry.single(
                        remembrance,
                        reminderTargetLabelProvider.apply(remembrance),
                        singleSelection.priority());
            }
        }

        List<ReminderSelectionEntry> refreshedCollections = new ArrayList<>();
        for (ReminderSelectionEntry entry : collectionSelections) {
            String key = entry.id().startsWith(COLLECTION_ID_PREFIX)
                    ? entry.id().substring(COLLECTION_ID_PREFIX.length())
                    : "";

            Optional<LibraryCollectionOption> option = availablePlannerCollections.stream()
                    .filter(candidate -> candidate.key().equals(key))
                    .findFirst();
            List<Remembrance> remembrances = option
                    .map(candidate -> resolveRemembrancesForCollection(candidate, allRemembrances))
                    .orElseGet(() -> remapSelectionRemembrances(entry, availableById));
            if (remembrances.isEmpty()) {
                continue;
            }

            refreshedCollections.add(ReminderSelectionEntry.collection(
                    key,
                    entry.label(),
                    entry.priority(),
                    remembrances,
                    entry.sourceTag()));
        }
        collectionSelections.clear();
        collectionSelections.addAll(refreshedCollections);

        List<ReminderSelectionEntry> refreshedCustom = new ArrayList<>();
        for (ReminderSelectionEntry entry : customSelections) {
            List<Remembrance> remembrances = remapSelectionRemembrances(entry, availableById);
            if (remembrances.isEmpty()) {
                continue;
            }

            String customName = entry.sourceTag() == null || entry.sourceTag().isBlank() ? entry.label() : entry.sourceTag();
            refreshedCustom.add(ReminderSelectionEntry.custom(customName, entry.label(), entry.priority(), remembrances));
        }
        customSelections.clear();
        customSelections.addAll(refreshedCustom);

        return refreshedSingle;
    }

    static ReminderSelectionEntry refreshSelectionsForFavoriteChange(
            ReminderSelectionEntry singleSelection,
            List<ReminderSelectionEntry> collectionSelections,
            long remembranceId,
            boolean nowFavorite,
            List<Remembrance> allRemembrances) {
        ReminderSelectionEntry refreshedSingle = singleSelection;
        if (singleSelection != null && !singleSelection.remembrances().isEmpty()) {
            Remembrance selected = singleSelection.remembrances().getFirst();
            if (selected.getId().orElse(-1L) == remembranceId) {
                refreshedSingle = ReminderSelectionEntry.single(
                        selected,
                        singleSelection.label(),
                        singleSelection.priority());
            }
        }

        List<ReminderSelectionEntry> refreshedCollections = new ArrayList<>();
        for (ReminderSelectionEntry entry : collectionSelections) {
            if (!entry.id().startsWith(COLLECTION_ID_PREFIX)) {
                refreshedCollections.add(entry);
                continue;
            }

            String key = entry.id().substring(COLLECTION_ID_PREFIX.length());
            List<Remembrance> remembrances;
            if (LibraryKeys.FAVORITES_COLLECTION_KEY.equals(key)) {
                List<Remembrance> favoriteRemembrances = new ArrayList<>(
                        allRemembrances.stream().filter(Remembrance::isFavorite).toList());
                favoriteRemembrances.removeIf(item -> item.getId().orElse(-1L) == remembranceId);
                if (nowFavorite) {
                    Remembrance added = findRemembranceById(allRemembrances, remembranceId);
                    if (added != null) {
                        favoriteRemembrances.add(added);
                    }
                }
                remembrances = favoriteRemembrances;
            } else {
                remembrances = entry.remembrances();
            }

            if (remembrances.isEmpty()) {
                continue;
            }

            refreshedCollections.add(ReminderSelectionEntry.collection(
                    key,
                    entry.label(),
                    entry.priority(),
                    remembrances,
                    entry.sourceTag()));
        }
        collectionSelections.clear();
        collectionSelections.addAll(refreshedCollections);

        return refreshedSingle;
    }

    static List<ScheduledReminderItem> gatherScheduledReminderItems(
            ReminderSelectionEntry singleSelection,
            List<ReminderSelectionEntry> collectionSelections,
            List<ReminderSelectionEntry> customSelections) {
        LinkedHashMap<Long, ScheduledReminderItem> deduplicated = new LinkedHashMap<>();

        if (singleSelection != null) {
            addSelectionItems(deduplicated, singleSelection);
        }
        collectionSelections.forEach(entry -> addSelectionItems(deduplicated, entry));
        customSelections.forEach(entry -> addSelectionItems(deduplicated, entry));

        return List.copyOf(deduplicated.values());
    }

    static List<Remembrance> resolveRemembrancesForCollection(LibraryCollectionOption option, List<Remembrance> allRemembrances) {
        if (option == null) {
            return List.of();
        }
        if (option.isAllCollections()) {
            return List.copyOf(allRemembrances);
        }
        if (option.isFavorites()) {
            return allRemembrances.stream().filter(Remembrance::isFavorite).toList();
        }
        if (option.isUncategorized()) {
            return allRemembrances.stream().filter(item -> item.getTags().isEmpty()).toList();
        }
        if (option.isTagCollection()) {
            String tagName = option.tagNameKey();
            return allRemembrances.stream()
                    .filter(item -> item.getTags().stream()
                            .anyMatch(tag -> tag.getName().equalsIgnoreCase(tagName)))
                    .toList();
        }
        return List.of();
    }

    static Remembrance findRemembranceById(List<Remembrance> allRemembrances, long remembranceId) {
        for (Remembrance remembrance : allRemembrances) {
            if (remembrance.getId().isEmpty()) {
                continue;
            }
            if (remembrance.getId().orElse(-1L) == remembranceId) {
                return remembrance;
            }
        }
        return null;
    }

    private static void addSelectionItems(
            Map<Long, ScheduledReminderItem> deduplicated, ReminderSelectionEntry selectionEntry) {
        NotificationPriority priority = NotificationPriority.fallback(selectionEntry.priority());
        for (Remembrance remembrance : selectionEntry.remembrances()) {
            if (remembrance.getId().isEmpty()) {
                continue;
            }

            long reminderId = remembrance.getId().orElse(-1L);
            ScheduledReminderItem candidate = new ScheduledReminderItem(remembrance, priority);
            deduplicated.merge(
                    reminderId,
                    candidate,
                    (existing, incoming) -> existing.priority().orderWeight()
                                    >= incoming.priority().orderWeight()
                            ? existing
                            : incoming);
        }
    }

    private static List<Remembrance> remapSelectionRemembrances(
            ReminderSelectionEntry selection, Map<Long, Remembrance> availableById) {
        List<Remembrance> remapped = new ArrayList<>();
        for (Remembrance remembrance : selection.remembrances()) {
            long id = remembrance.getId().orElse(-1L);
            Remembrance refreshed = availableById.get(id);
            if (refreshed != null) {
                remapped.add(refreshed);
            }
        }
        return remapped;
    }

    private LibraryReminderSelectionSupport() {}
}
