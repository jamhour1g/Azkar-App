package com.azkar.data.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

///
/// JPA converter between [Locale] and its database [String] representation via
/// [Locale#toLanguageTag].
///
/// - Stores locales as BCP 47 language tags using [Locale#toLanguageTag].
/// - Restores locale instances using [Locale#forLanguageTag].
/// - Accepts and returns `null` to interoperate with JPA when entity columns are nullable.
///
/// ## Null policy
/// This converter is not intended to process `null` values in domain/business logic.
/// The `@Nullable` types exist solely to support JPA reading/writing nullable columns.
/// Call sites should avoid passing `null`; prefer a concrete default locale.
/// (e.g., [Locale#ROOT] to represent “no locale.”)
///
/// ## Usage
/// ```java
/// var c = new LocaleStringConverter();
/// String db = c.convertToDatabaseColumn(Locale.forLanguageTag("ar-EG")); // "ar-EG"
/// Locale locale = c.convertToEntityAttribute("en-US"); // Locale.forLanguageTag("en-US")
/// ```
///
/// @see Locale#toLanguageTag()
/// @see Locale#forLanguageTag(String)
@Converter(autoApply = true)
public final class LocaleStringConverter implements AttributeConverter<@Nullable Locale, @Nullable String> {

    /// Converts a [Locale] to a BCP 47 language tag [String] for persistence.
    ///
    /// @param attribute the [Locale] to convert; may be `null` when JPA writes a nullable column
    /// @return the BCP 47 tag as a [String], or `null` if input was `null`
    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable Locale attribute) {
        return attribute != null ? attribute.toLanguageTag() : null;
    }

    /// Converts a BCP 47 language tag [String] into a [Locale] instance.
    ///
    /// @param dbData the stored locale tag; may be `null` when JPA reads a nullable column
    /// @return the corresponding [Locale], or `null` if input was `null`
    @Override
    public @Nullable Locale convertToEntityAttribute(@Nullable String dbData) {
        return dbData != null ? Locale.forLanguageTag(dbData) : null;
    }
}
