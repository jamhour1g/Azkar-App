package com.azkar.ui.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.azkar.domain.model.HadithGrade
import com.azkar.domain.model.Remembrance
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.remembranceCreateStepTags
import com.azkar.generated.resources.remembranceCreateStepText
import com.azkar.generated.resources.remembranceCreateStepTranslations
import com.azkar.i18n.isSameLanguageAs
import com.azkar.ui.library.model.RemembranceTranslationEntry
import com.azkar.ui.library.model.TagOption
import org.jetbrains.compose.resources.StringResource
import java.util.Locale

/**
 * The pages the "new remembrance" form walks through, in order.
 *
 * An enum rather than a `List<String>` because the page is named in two places — the header's subtitle
 * and the step bar's position — and a page that could only be referred to by index is a page whose
 * order nothing else is allowed to have an opinion about.
 *
 * Three and not four: text, translations and tags. The grading and the source ride on the text page
 * rather than getting one of their own, because neither is anything you think about without the words
 * in front of you — a grade with no passage to grade has nothing to be a grade *of*.
 *
 * @property subtitleResource the one sentence the header says while this page is up. Carried as a
 * resource rather than as text because the header is a composable and this is not.
 */
internal enum class RemembranceCreateStep {
    /** The Arabic passage, its grading, and where it came from. */
    TEXT,

    /** The same passage in other languages, with an explanation for each. */
    TRANSLATIONS,

    /** The tags the reader will look for it under. */
    TAGS,
    ;

    val subtitleResource: StringResource
        get() =
            when (this) {
                TEXT -> Res.string.remembranceCreateStepText
                TRANSLATIONS -> Res.string.remembranceCreateStepTranslations
                TAGS -> Res.string.remembranceCreateStepTags
            }
}

/**
 * The half-written passage behind [RemembranceCreateDialog].
 *
 * Held by the collection detail state so it survives recomposition while the dialog is open, then
 * cleared when the dialog closes. A class rather than a data class on purpose — equality over two
 * `MutableState` delegates compares state objects rather than the text the user can see.
 *
 * The passage is Arabic and is not a "translation", which is the whole reason [text] is its own field
 * rather than the first entry of [translations]. Every other language is optional and fills in around
 * it, so a form that treated them as equals would put a required field behind a chip the user may never
 * press.
 */
@Stable
class RemembranceCreateDraft(
    /** The language [text] is written in. Almost always Arabic; carried rather than assumed. */
    val textLocale: Locale,
) : TagDraftView {
    /** The Arabic passage, which is the one thing this form cannot be done without. */
    var text: String by mutableStateOf("")
        private set

    /** How the tradition grades it. Meaningful, and optional: most azkar carry no grade at all. */
    var grade: HadithGrade by mutableStateOf(HadithGrade.UNSPECIFIED)
        private set

    /** Where it came from — a book, a collection, a page. Free text, and no row of its own. */
    var source: String by mutableStateOf("")
        private set

    /**
     * Every language other than [textLocale] this passage has been given.
     *
     * A list of [RemembranceTranslationEntry] rather than a map because a half-typed language needs
     * somewhere to live before it has anything to say, and because the chips and the blocks both want
     * to iterate "the languages this remembrance has".
     *
     * Deliberately *not* including [textLocale]: the passage above is already that language's version,
     * and a second box for it would be a field the user fills in only to have it shadowed.
     */
    var translations: List<RemembranceTranslationEntry> by mutableStateOf(emptyList())
        private set

    override var tags: List<String> by mutableStateOf(emptyList())
        private set

    override var tagQuery: String by mutableStateOf("")
        private set

    override var newTagName: String by mutableStateOf("")
        private set

    /**
     * Whether to scold the user about an empty passage.
     *
     * Set only once they have tried to continue, so an untouched form does not open by telling someone
     * off for a field they have not had the chance to type in.
     */
    var textRejected: Boolean by mutableStateOf(false)
        private set

    /** The passage as storage should receive it: trimmed, and never blank. */
    val submittedText: String get() = text.trim()

    /** The source as storage should receive it: absent rather than a run of spaces. */
    val submittedSource: String? get() = source.trim().ifEmpty { null }

    override val newTagAcceptable: Boolean
        get() {
            val typed = newTagName.trim()
            return typed.isNotEmpty() && !isTagSelected(typed)
        }

    /**
     * Every language this passage will be saved in, keyed by locale.
     *
     * The passage itself is folded in first so that [text] and [translations] are two halves of one
     * answer rather than two fields that have to be kept in step. A language whose box was opened and
     * then abandoned is dropped here rather than refused on the way in, because an empty translation is
     * the normal state of one someone has just clicked "add translation" for.
     *
     * The explanation is written beside the translation rather than under it because a translator and
     * a commentator are two different jobs, and plenty of translations have no explanation at all.
     */
    val submittedTranslations: Map<Locale, Remembrance.Translations>
        get() =
            buildMap {
                put(
                    textLocale,
                    Remembrance.Translations(
                        explanationPair = Remembrance.Translations.Pair(id = null, text = ""),
                        translationPair = Remembrance.Translations.Pair(id = null, text = submittedText),
                    ),
                )
                translations
                    .filterNot { it.isBlank }
                    .forEach { entry ->
                        put(
                            entry.locale,
                            Remembrance.Translations(
                                explanationPair =
                                    Remembrance.Translations.Pair(
                                        id = null,
                                        text = entry.explanation?.trim().orEmpty(),
                                    ),
                                translationPair =
                                    Remembrance.Translations.Pair(
                                        id = null,
                                        text = entry.text.trim(),
                                    ),
                            ),
                        )
                    }
            }

    fun updateText(value: String) {
        text = value
        if (submittedText.isNotEmpty()) {
            textRejected = false
        }
    }

    fun updateGrade(value: HadithGrade) {
        grade = value
    }

    fun updateSource(value: String) {
        source = value
    }

    override fun updateTagQuery(value: String) {
        tagQuery = value
    }

    override fun updateNewTagName(value: String) {
        newTagName = value
    }

    /**
     * Whether [locale] already has a version open, which is what decides whether its chip is offered.
     *
     * Compared by language rather than by tag so an Arabic entry added as `ar` is not offered a second
     * time as `ar-EG`. Two entries for one language would resolve to the same row on the way back in,
     * and whichever lost would take the other's text with it.
     */
    fun hasTranslationFor(locale: Locale): Boolean = translations.any { it.locale.isSameLanguageAs(locale) }

    /** Opens a version in [locale], or reports that it did not. Refused rather than silently ignored. */
    fun addTranslation(locale: Locale): Boolean {
        if (hasTranslationFor(locale)) {
            return false
        }
        translations = translations + RemembranceTranslationEntry(locale = locale)
        return true
    }

    fun updateTranslationText(
        locale: Locale,
        value: String,
    ) {
        translations = translations.map { entry -> entry.replacing(locale) { it.copy(text = value) } }
    }

    fun updateExplanation(
        locale: Locale,
        value: String,
    ) {
        translations = translations.map { entry -> entry.replacing(locale) { it.copy(explanation = value) } }
    }

    /**
     * Closes the version in [locale].
     *
     * Removes it rather than blanking it, because those are different outcomes: a blank entry is a
     * version nobody finished, and this is a version the user looked at and decided against.
     */
    fun removeTranslation(locale: Locale) {
        translations = translations.filterNot { it.locale.isSameLanguageAs(locale) }
    }

    override fun isTagSelected(name: String): Boolean = tags.any { it.equals(name, ignoreCase = true) }

    override fun toggleTag(name: String) {
        val wanted = name.trim()
        if (wanted.isEmpty()) {
            return
        }
        tags =
            if (isTagSelected(wanted)) {
                tags.filterNot { it.equals(wanted, ignoreCase = true) }
            } else {
                tags + wanted
            }
    }

    override fun addTag(raw: String): Boolean {
        val wanted = raw.trim()
        if (wanted.isEmpty() || isTagSelected(wanted)) {
            return false
        }
        tags = tags + wanted
        newTagName = ""
        return true
    }

    override fun tagOptions(available: List<TagOption>): List<TagOption> {
        val unheard =
            tags
                .filter { chosen -> available.none { it.value.equals(chosen, ignoreCase = true) } }
                .map(TagOption::of)
        val options = (available + unheard).distinctBy { it.value.lowercase() }
        val query = tagQuery.trim()
        if (query.isEmpty()) {
            return options
        }
        return options.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.value.contains(query, ignoreCase = true) ||
                isTagSelected(it.value)
        }
    }

    override fun clearTags() {
        tags = emptyList()
    }

    /**
     * The confirm button's click handler. Reports whether the dialog may close.
     *
     * The passage may not be blank: an empty remembrance is not a shorter one, and a collection made
     * of them would be a shelf of empty cards.
     */
    fun submit(): Boolean {
        if (submittedText.isEmpty()) {
            textRejected = true
            return false
        }
        return true
    }
}

/** Applies [change] to the entry in [locale] and leaves every other language alone. */
private fun RemembranceTranslationEntry.replacing(
    locale: Locale,
    change: (RemembranceTranslationEntry) -> RemembranceTranslationEntry,
): RemembranceTranslationEntry = if (locale.isSameLanguageAs(this.locale)) change(this) else this
