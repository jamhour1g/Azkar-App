package com.azkar.data.repo.jpa;

/// Utility methods for building safe JPQL query parameters.
///
/// ### LIKE-pattern escaping
/// JPQL `LIKE` treats `%`, `_`, and `\` as special characters.
/// Any user-supplied text used inside a `LIKE` clause must be escaped
/// via [#escapeLikePattern(String)] before wrapping with `%…%`.
/// The corresponding `@Query` must include `ESCAPE '\\'` so the
/// database knows `\` is the escape character.
///
/// Example:
/// ```java
/// String safe = "%" + JpqlUtils.escapeLikePattern(userInput) + "%";
/// repo.searchByTranslationText(locale, safe);   // @Query uses ESCAPE '\\'
/// ```
public final class JpqlUtils {

    private JpqlUtils() {
        throw new UnsupportedOperationException(
            "This is a utility class and cannot be instantiated"
        );
    }

    /// Escapes JPQL LIKE special characters (`\`, `%`, `_`) in the given input
    /// so that they are matched literally.
    ///
    /// The escape character is `\` — all JPQL queries using the escaped value
    /// **must** include `ESCAPE '\\'` in the `LIKE` clause.
    ///
    /// @param input the raw user input; must not be `null`
    /// @return the escaped string safe for use in a `LIKE` parameter
    public static String escapeLikePattern(String input) {
        return input
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }
}
