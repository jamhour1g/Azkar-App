package com.azkar.data.converter;

import com.azkar.data.entity.DatabaseHadithGrade;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

///
/// JPA converter between [DatabaseHadithGrade] and its database [String] representation.
///
/// - Stores the enum as its [name()][DatabaseHadithGrade#name].
/// - Restores the enum via [DatabaseHadithGrade#valueOf].
/// - Accepts and returns `null` to interoperate with JPA when entity columns are nullable.
///
/// ## Null policy
/// This converter is not intended to process `null` values in application/business logic.
/// The `@Nullable` types exist solely, so JPA can read/write nullable columns without throwing.
/// Call sites should avoid passing `null`; prefer a concrete enum (e.g., [DatabaseHadithGrade#UNSPECIFIED])
/// to represent “no grade” at the domain level.
///
/// ## Usage
/// ```java
/// var c = new HadithGradeConverter();
/// String db = c.convertToDatabaseColumn(DatabaseHadithGrade.SAHIH); // "SAHIH"
/// DatabaseHadithGrade g = c.convertToEntityAttribute("HASAN"); // DatabaseHadithGrade.HASAN
/// ```
///
/// @see DatabaseHadithGrade
///
@Converter(autoApply = true)
public class HadithGradeConverter implements AttributeConverter<@Nullable DatabaseHadithGrade, @Nullable String> {

    /// Converts a [DatabaseHadithGrade] enum to its [name()][DatabaseHadithGrade#name] for persistence.
    ///
    /// @param attribute the enum value; may be `null` when JPA writes a nullable column
    /// @return the enum name, or `null` if the input is `null`
    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable DatabaseHadithGrade attribute) {
        return attribute != null ? attribute.name() : null;
    }

    /// Converts a database string into a [DatabaseHadithGrade] enum.
    ///
    /// @param dbData the stored string; may be `null` when JPA reads a nullable column
    /// @return the corresponding enum, or `null` if the input is `null`
    /// @throws IllegalArgumentException if `dbData` is non-null and not a valid enum constant
    @Override
    public @Nullable DatabaseHadithGrade convertToEntityAttribute(@Nullable String dbData) {
        return dbData != null ? DatabaseHadithGrade.valueOf(dbData) : null;
    }
}
