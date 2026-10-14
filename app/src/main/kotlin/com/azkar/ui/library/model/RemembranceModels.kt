package com.azkar.ui.library.model

import com.azkar.i18n.isSameLanguageAs
import com.azkar.i18n.supportedLocales
import java.util.Locale

/**
 * The languages the translation chips should still offer, for a passage written in [textLocale].
 *
 * The same rule as a collection's [missingLanguages], and a separate function rather than a shared one
 * because the two are offered over different things: a collection's chips exclude the language its
 * *name* is in, while these exclude the language the *passage* is in, and a name and a passage are
 * routinely in different languages — an English-named collection of Arabic azkar is the normal case
 * here, and getting it backwards would offer a chip that saves nothing.
 *
 * [uiLocale] is only a fallback for a caller that does not know the passage's language; script
 * direction is never used to identify a language.
 */
fun missingRemembranceLanguages(
    existing: Collection<RemembranceTranslationEntry>,
    textLocale: Locale,
    uiLocale: Locale,
): List<Locale> {
    val namedIn = textLocale.takeIf { it.language.isNotEmpty() } ?: uiLocale
    return supportedLocales.filter { candidate ->
        !candidate.isSameLanguageAs(namedIn) &&
            existing.none { it.locale.isSameLanguageAs(candidate) }
    }
}
