package com.azkar.data.converter;

import com.azkar.data.model.HadithGrade;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

///
/// JPA converter between [HadithGrade] and its database [String] representation.
///
/// - Stores the enum as its [name()][HadithGrade#name].
/// - Restores the enum via [HadithGrade#valueOf].
/// - Accepts and returns `null` to interoperate with JPA when entity columns are nullable.
///
/// ## Null policy
/// This converter is not intended to process `null` values in application/business logic.
/// The `@Nullable` types exist solely, so JPA can read/write nullable columns without throwing.
/// Call sites should avoid passing `null`; prefer a concrete enum (e.g., [HadithGrade#UNSPECIFIED])
/// to represent “no grade” at the domain level.
///
/// ## Usage
/// ```java
/// var c = new HadithGradeConverter();
/// String db = c.convertToDatabaseColumn(HadithGrade.SAHIH); // "SAHIH"
/// HadithGrade g = c.convertToEntityAttribute("HASAN"); // HadithGrade.HASAN
/// ```
///
/// @see HadithGrade
///
@Converter(autoApply = true)
public class HadithGradeConverter implements AttributeConverter<@Nullable HadithGrade, @Nullable String> {

    /// Converts a [HadithGrade] enum to its [name()][HadithGrade#name] for persistence.
    ///
    /// @param attribute the enum value; may be `null` when JPA writes a nullable column
    /// @return the enum name, or `null` if the input is `null`
    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable HadithGrade attribute) {
        return attribute != null ? attribute.name() : null;
    }

    /// Converts a database string into a [HadithGrade] enum.
    ///
    /// @param dbData the stored string; may be `null` when JPA reads a nullable column
    /// @return the corresponding enum, or `null` if the input is `null`
    /// @throws IllegalArgumentException if `dbData` is non-null and not a valid enum constant
    @Override
    public @Nullable HadithGrade convertToEntityAttribute(@Nullable String dbData) {
        return dbData != null ? HadithGrade.valueOf(dbData) : null;
    }
}
