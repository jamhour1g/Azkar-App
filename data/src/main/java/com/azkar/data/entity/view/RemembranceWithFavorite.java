package com.azkar.data.entity.view;

import com.azkar.data.entity.DatabaseHadithGrade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "remembrance_with_favorite")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RemembranceWithFavorite {
    @Id
    @Nullable
    Long id;

    @Nullable
    String source; // DB allows nulls; keep non-null default semantics at the app layer if desired

    @Nullable
    DatabaseHadithGrade grade; // nullable in DB

    @Column(name = "created_at")
    @Nullable
    Instant createdAt;

    @Column(name = "updated_at")
    @Nullable
    Instant updatedAt;

    @Column(name = "is_favorite")
    boolean favorite;
}
