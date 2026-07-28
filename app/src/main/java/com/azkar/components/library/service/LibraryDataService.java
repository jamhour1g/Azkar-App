package com.azkar.components.library.service;

import com.azkar.components.library.model.LibrarySnapshot;
import com.azkar.data.config.DomainServiceContext;
import com.azkar.domain.model.Remembrance;
import com.azkar.domain.model.Tag;
import com.azkar.domain.model.impl.RemembranceImpl;
import com.azkar.domain.model.impl.TagImpl;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LibraryDataService {

    public LibrarySnapshot fetchSnapshot() {
        try (var context = new DomainServiceContext()) {
            List<Remembrance> remembrances =
                    new ArrayList<>(context.remembranceService().findAll());
            remembrances.sort(
                    Comparator.comparingLong((Remembrance item) -> item.getId().orElse(Long.MAX_VALUE))
                            .reversed());

            Set<String> collections = new HashSet<>();
            for (Remembrance remembrance : remembrances) {
                for (Tag tag : remembrance.getTags()) {
                    collections.add(tag.getName());
                }
            }

            List<String> options = new ArrayList<>(
                    collections.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());
            return new LibrarySnapshot(List.copyOf(remembrances), List.copyOf(options), Instant.now());
        }
    }

    public void addTagToRemembrance(DomainServiceContext context, Remembrance remembrance, String newTagName) {
        if (newTagName == null || newTagName.isBlank()) {
            return;
        }
        String normalizedNewTag = newTagName.trim().toLowerCase(Locale.ROOT);
        LinkedHashMap<String, Tag> tagsByName = new LinkedHashMap<>();
        for (Tag tag : remembrance.getTags()) {
            tagsByName.put(tag.getName().toLowerCase(Locale.ROOT), tag);
        }

        if (!tagsByName.containsKey(normalizedNewTag)) {
            Tag tagToAttach =
                    context.tagService().findByNameIgnoreCase(newTagName).orElse(new TagImpl(null, newTagName, null));
            tagsByName.put(normalizedNewTag, tagToAttach);
        }

        Set<Tag> mergedTags = new LinkedHashSet<>(tagsByName.values());
        Remembrance updated = new RemembranceImpl(
                remembrance.getId().orElse(null),
                remembrance.getGrade(),
                remembrance.isFavorite(),
                remembrance.getSource().orElse(null),
                remembrance.getCreatedAt().orElse(null),
                remembrance.getUpdatedAt().orElse(null),
                remembrance.getTranslations(),
                mergedTags);

        context.remembranceService().save(updated);
    }
}
