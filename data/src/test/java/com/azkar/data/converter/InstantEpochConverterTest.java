package com.azkar.data.converter;

import static org.junit.jupiter.api.Assertions.*;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InstantEpochConverterTest {

    private final InstantEpochConverter converter = new InstantEpochConverter();

    @ParameterizedTest(name = "Round-trip from epoch seconds: {0}")
    @ValueSource(
            longs = {
                -62167219200L, // 0001-01-01T00:00:00Z
                -2208988800L, // 1900-01-01T00:00:00Z
                -1L, // before epoch
                0L, // epoch
                1L, // just after epoch
                915148800L, // 1999-01-01T00:00:00Z
                1609459200L, // 2021-01-01T00:00:00Z
                253402300799L // 9999-12-31T23:59:59Z
            })
    @DisplayName("Should convert epoch seconds to Instant and back consistently")
    void roundTrip_fromEpochSeconds_preservesValue(long epochSeconds) {
        Instant instant = converter.convertToEntityAttribute(epochSeconds);
        Long backToDb = converter.convertToDatabaseColumn(instant);

        assertEquals(
                epochSeconds,
                backToDb,
                () -> "Round-trip mismatch for epoch seconds=%d. Converted Instant=%s, Back to DB=%d"
                        .formatted(epochSeconds, instant, backToDb));
    }

    @Test
    @DisplayName("Should convert Instant with nanos to DB seconds and back, truncating sub-second precision")
    void roundTrip_fromInstant_withNanos_truncatesToSeconds() {
        Instant original = Instant.ofEpochSecond(1_700_000_000L, 987_654_321);
        Long dbValue = converter.convertToDatabaseColumn(original);
        Instant roundTripped = converter.convertToEntityAttribute(dbValue);
        Instant expected = original.truncatedTo(ChronoUnit.SECONDS);

        assertEquals(
                original.getEpochSecond(),
                dbValue,
                () -> "Expected DB value to equal epochSecond=%d but was %d for Instant=%s"
                        .formatted(original.getEpochSecond(), dbValue, original));
        assertEquals(
                expected,
                roundTripped,
                () -> "Expected round-tripped Instant to be truncated-to-seconds=%s but was %s (original=%s)"
                        .formatted(expected, roundTripped, original));
        assertEquals(0, roundTripped.getNano(), () -> "Expected nanos to be 0 after round-trip but were %d"
                .formatted(roundTripped.getNano()));
    }

    @Test
    @DisplayName("Should convert Instant.now() to DB and back with second-level precision")
    void roundTrip_fromInstantNow_preservesEpochSeconds() {
        Instant now = Instant.now();
        Long dbValue = converter.convertToDatabaseColumn(now);
        Instant roundTripped = converter.convertToEntityAttribute(dbValue);
        Instant expected = now.truncatedTo(ChronoUnit.SECONDS);

        assertEquals(
                expected,
                roundTripped,
                () -> "Expected Instant.now() to round-trip as %s but got %s\nOriginal nanos: %d"
                        .formatted(expected, roundTripped, now.getNano()));
    }

    @Test
    @DisplayName("Should return null when converting null Instant to DB column")
    void convertToDatabaseColumn_nullInstant_returnsNull() {
        Long result = converter.convertToDatabaseColumn(null);
        assertNull(result, "Expected result to be null when converting null Instant");
    }

    @Test
    @DisplayName("Should return null when converting null DB value to Instant")
    void convertToEntityAttribute_nullLong_returnsNull() {
        Instant result = converter.convertToEntityAttribute(null);
        assertNull(result, "Expected result to be null when converting null DB value");
    }

    @Test
    @DisplayName("Should throw DateTimeException for out-of-range epoch seconds")
    void convertToEntityAttribute_outOfRange_throwsDateTimeException() {
        assertThrows(
                DateTimeException.class,
                () -> converter.convertToEntityAttribute(Long.MAX_VALUE),
                () -> "Expected DateTimeException for out-of-range epoch seconds=%d".formatted(Long.MAX_VALUE));
    }
}
