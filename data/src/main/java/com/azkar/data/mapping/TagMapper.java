package com.azkar.data.mapping;

import com.azkar.data.entity.TagEntity;
import com.azkar.domain.model.Tag;
import com.azkar.domain.model.impl.TagImpl;

public final class TagMapper {

    private TagMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static Tag toTag(TagEntity tagEntity) {
        return TagImpl.builder().id(tagEntity.getId()).name(tagEntity.getName()).build();
    }

    public static TagEntity fromTag(Tag tag) {
        return TagEntity.builder()
                .id(tag.getId().orElse(null))
                .name(tag.getName())
                .build();
    }
}
