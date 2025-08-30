package com.azkar.data.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocaleStringConverterTest {

    private final LocaleStringConverter converter = new LocaleStringConverter();

    @Test
    @DisplayName("Should convert Locale to language tag string and back")
    void roundTrip_fromLocale_preservesValue() {
        Locale original = Locale.forLanguageTag("en-US");
        String dbValue = converter.convertToDatabaseColumn(original);
        Locale roundTripped = converter.convertToEntityAttribute(dbValue);

        assertEquals("en-US", dbValue, "Expected DB value to be 'en-US'");
        assertEquals(
            original.toLanguageTag(),
            roundTripped.toLanguageTag(),
            () ->
                "Expected round-trip to preserve language tag '%s' but got '%s'".formatted(
                    original.toLanguageTag(),
                    roundTripped.toLanguageTag()
                )
        );
    }

    @Test
    @DisplayName("Should convert null Locale to null DB string")
    void convertToDatabaseColumn_nullLocale_returnsNull() {
        String result = converter.convertToDatabaseColumn(null);
        assertNull(result, "Expected null result when Locale is null");
    }

    @Test
    @DisplayName("Should convert null DB string to null Locale")
    void convertToEntityAttribute_nullString_returnsNull() {
        Locale result = converter.convertToEntityAttribute(null);
        assertNull(result, "Expected null result when DB string is null");
    }

    @Test
    @DisplayName("Should handle empty string gracefully")
    void convertToEntityAttribute_emptyString_returnsRootLocale() {
        Locale result = converter.convertToEntityAttribute("");
        assertEquals(Locale.ROOT, result, () ->
            "Expected empty string to convert to ROOT locale but got '%s'".formatted(
                result.toLanguageTag()
            )
        );
    }
}
