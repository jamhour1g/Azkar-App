package com.azkar.data.mapping;

import com.azkar.data.entity.RemembranceEntity;
import com.azkar.data.entity.TagEntity;
import com.azkar.domain.model.Remembrance;
import com.azkar.domain.model.Tag;
import com.azkar.domain.model.impl.RemembranceImpl;
import java.util.*;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public final class RemembranceMapper {

    private RemembranceMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static Remembrance toRemembrance(RemembranceEntity remembranceEntity) {

        Set<Tag> tagList =
                remembranceEntity.getTags().stream().map(TagMapper::toTag).collect(Collectors.toUnmodifiableSet());

        return RemembranceImpl.builder()
                .id(remembranceEntity.getId())
                .source(remembranceEntity.getSource())
                .grade(HadithGradeMapper.toHadithGrade(remembranceEntity.getGrade()))
                .favorite(remembranceEntity.isFavorited())
                .createdAt(remembranceEntity.getCreatedAt())
                .updatedAt(remembranceEntity.getUpdatedAt())
                .translations(groupByLocale(remembranceEntity))
                .tags(tagList)
                .build();
    }

    public static RemembranceEntity fromRemembrance(Remembrance remembrance) {
        List<TagEntity> tags =
                remembrance.getTags().stream().map(TagMapper::fromTag).toList();

        RemembranceEntity.Builder builder = RemembranceEntity.builder()
                .id(remembrance.getId().orElse(null))
                .source(remembrance.getSource().orElse(null))
                .grade(HadithGradeMapper.fromHadithGrade(remembrance.getGrade()))
                .favorite(remembrance.isFavorite())
                .addTags(tags);

        remembrance.getTranslations().forEach((loc, trEntity) -> {
            builder.addTranslation(loc, trEntity.translationText());
            builder.addExplanation(loc, trEntity.explanationText());
        });

        return builder.build();
    }

    private static Map<Locale, Remembrance.Translations> groupByLocale(RemembranceEntity remembranceEntity) {
        int expectedSize = Math.max(
                remembranceEntity.getTranslations().size(),
                remembranceEntity.getExplanations().size());

        Map<Locale, TranslationsData> dataByLocale = HashMap.newHashMap(expectedSize);

        remembranceEntity.getTranslations().forEach((loc, trEntity) -> dataByLocale
                .computeIfAbsent(loc, ignored -> new TranslationsData())
                .setTranslation(trEntity.getText()));

        remembranceEntity.getExplanations().forEach((loc, exEntity) -> dataByLocale
                .computeIfAbsent(loc, ignored -> new TranslationsData())
                .setExplanation(exEntity.getText()));

        // Fail fast if any side is missing
        for (Map.Entry<Locale, TranslationsData> entry : dataByLocale.entrySet()) {
            TranslationsData translations = entry.getValue();

            if (translations.getTranslation() == null) {
                throw new IllegalArgumentException("Missing translation for locale: " + entry.getKey());
            }

            if (translations.getExplanation() == null) {
                throw new IllegalArgumentException("Missing explanation for locale: " + entry.getKey());
            }
        }

        return dataByLocale.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, dataEntry -> {
            String explanation = dataEntry.getValue().getExplanation();
            String translation = dataEntry.getValue().getTranslation();
            return new Remembrance.Translations(
                    Objects.requireNonNull(explanation), Objects.requireNonNull(translation));
        }));
    }

    private static final class TranslationsData {

        private @Nullable String translation;
        private @Nullable String explanation;

        private void setTranslation(@Nullable String translation) {
            this.translation = translation;
        }

        private void setExplanation(@Nullable String explanation) {
            this.explanation = explanation;
        }

        @Nullable
        private String getTranslation() {
            return translation;
        }

        @Nullable
        private String getExplanation() {
            return explanation;
        }
    }
}
