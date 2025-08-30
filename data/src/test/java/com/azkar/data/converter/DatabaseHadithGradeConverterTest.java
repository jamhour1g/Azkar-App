package com.azkar.data.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.azkar.data.entity.DatabaseHadithGrade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DatabaseHadithGradeConverterTest {

    private final HadithGradeConverter converter = new HadithGradeConverter();

    @ParameterizedTest(name = "Round-trip conversion for {0}")
    @EnumSource(DatabaseHadithGrade.class)
    @DisplayName(
        "Round-trip conversion of DatabaseHadithGrade enum should be consistent"
    )
    void roundTripConversion_validEnum_preservesValue(
        DatabaseHadithGrade originalGrade
    ) {
        String dbValue = converter.convertToDatabaseColumn(originalGrade);
        DatabaseHadithGrade convertedGrade = converter.convertToEntityAttribute(
            dbValue
        );

        assertEquals(originalGrade, convertedGrade, () ->
            "Round-trip failed for enum value '%s'.\nConverted to DB string: '%s'\nThen converted back to enum: '%s'".formatted(
                originalGrade,
                dbValue,
                convertedGrade
            )
        );
    }

    @Test
    @DisplayName(
        "Should return null when converting null DatabaseHadithGrade to DB column"
    )
    void convertToDatabaseColumn_nullEnum_returnsNull() {
        String result = converter.convertToDatabaseColumn(null);
        assertNull(
            result,
            "Expected result to be null when converting null DatabaseHadithGrade"
        );
    }

    @Test
    @DisplayName(
        "Should return null when converting null DB string to DatabaseHadithGrade enum"
    )
    void convertToEntityAttribute_nullString_returnsNull() {
        DatabaseHadithGrade result = converter.convertToEntityAttribute(null);
        assertNull(
            result,
            "Expected result to be null when converting null DB string"
        );
    }
}
