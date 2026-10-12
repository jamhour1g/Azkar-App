package com.azkar.i18n

import com.azkar.domain.model.Remembrance
import java.util.Locale

/** Translation resolution for a single [Remembrance], ported verbatim from the JavaFX helper. */
object RemembranceI18n {
    data class LocalizedTexts(
        val translation: String,
        val explanation: String,
    )

    /**
     * Exact locale, then same language, then the opposite language of the preferred
     * locale, then whatever the remembrance happens to carry.
     */
    fun resolveTranslations(
        remembrance: Remembrance,
        preferredLocale: Locale?,
    ): Remembrance.Translations? {
        val translations = remembrance.translations
        val normalizedPreferred = preferredLocale ?: Locale.getDefault()
        val oppositeFallback =
            if (normalizedPreferred.isSameLanguageAs(ARABIC)) {
                ENGLISH
            } else {
                ARABIC
            }
        return listOfNotNull(
            translations[normalizedPreferred],
            byLanguage(translations, normalizedPreferred),
            byLanguage(translations, oppositeFallback),
            translations.values.firstOrNull(),
        ).firstOrNull()
    }

    fun resolveTexts(
        remembrance: Remembrance,
        preferredLocale: Locale?,
        translationFallback: String,
        explanationFallback: String,
    ): LocalizedTexts {
        val resolved = resolveTranslations(remembrance, preferredLocale)
        return LocalizedTexts(
            translation = resolved?.translationPair?.text?.takeIf(::hasText) ?: translationFallback,
            explanation = resolved?.explanationPair?.text?.takeIf(::hasText) ?: explanationFallback,
        )
    }

    private fun byLanguage(
        translations: Map<Locale, Remembrance.Translations>,
        targetLocale: Locale?,
    ): Remembrance.Translations? {
        val language = targetLocale?.language
        if (language.isNullOrBlank()) return null
        return translations.entries
            .firstOrNull { language.equals(it.key.language, ignoreCase = true) }
            ?.value
    }

    private fun hasText(value: String?): Boolean = !value.isNullOrBlank()
}
