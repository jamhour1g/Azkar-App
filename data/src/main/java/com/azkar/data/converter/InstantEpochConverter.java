package com.azkar.data.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

///
/// JPA converter between [Instant] and its database [Long] epoch seconds representation.
///
/// - Stores the temporal value as [Instant#getEpochSecond].
/// - Restores the temporal value via [Instant#ofEpochSecond].
/// - Accepts and returns `null` to interoperate with JPA when entity columns are nullable.
///
/// ## Null policy
/// This converter is not intended to process `null` values in domain/business logic.
/// The `@Nullable` types exist solely to satisfy JPA’s handling of nullable columns.
/// Call sites should avoid passing `null`; prefer a concrete value or use domain-level defaults.
/// (e.g., [Instant#now] to represent “now” at the domain level.)
///
/// ## Usage
/// ```java
/// var c = new InstantEpochConverter();
/// Long db = c.convertToDatabaseColumn(Instant.parse("2024-01-01T00:00:00Z")); // 1704067200
/// Instant i = c.convertToEntityAttribute(1704067200L); // 2024-01-01T00:00:00Z
/// ```
///
/// @see Instant#getEpochSecond()
/// @see Instant#ofEpochSecond(long)
@Converter(autoApply = true)
public final class InstantEpochConverter
    implements AttributeConverter<@Nullable Instant, @Nullable Long> {

    /// Converts an [Instant] to its epoch seconds [Long] representation.
    ///
    /// @param attribute the [Instant] to store; may be `null` when JPA writes a nullable column
    /// @return the number of seconds since epoch, or `null` if input was `null`
    @Override
    public @Nullable Long convertToDatabaseColumn(@Nullable Instant attribute) {
        return attribute != null ? attribute.getEpochSecond() : null;
    }

    /// Converts an epoch seconds [Long] value into an [Instant].
    ///
    /// @param dbData the stored epoch value; may be `null` when JPA reads a nullable column
    /// @return the [Instant] restored from the epoch value, or `null` if input was `null`
    @Override
    public @Nullable Instant convertToEntityAttribute(@Nullable Long dbData) {
        return dbData != null ? Instant.ofEpochSecond(dbData) : null;
    }
}
