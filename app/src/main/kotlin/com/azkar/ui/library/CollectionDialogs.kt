package com.azkar.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.azkar.domain.model.LocalizedPair
import com.azkar.domain.model.RemembranceCollection
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionsActionCancel
import com.azkar.generated.resources.collectionsActionChooseCover
import com.azkar.generated.resources.collectionsActionCreateCollection
import com.azkar.generated.resources.collectionsActionDelete
import com.azkar.generated.resources.collectionsActionNext
import com.azkar.generated.resources.collectionsActionRemoveCover
import com.azkar.generated.resources.collectionsActionSave
import com.azkar.generated.resources.collectionsActionStepBack
import com.azkar.generated.resources.collectionsDialogChooseCoverTitle
import com.azkar.generated.resources.collectionsDialogClose
import com.azkar.generated.resources.collectionsDialogCoverBusy
import com.azkar.generated.resources.collectionsDialogCoverChosenBody
import com.azkar.generated.resources.collectionsDialogCoverChosenTitle
import com.azkar.generated.resources.collectionsDialogCoverLabel
import com.azkar.generated.resources.collectionsDialogCoverNoneBody
import com.azkar.generated.resources.collectionsDialogCoverNoneTitle
import com.azkar.generated.resources.collectionsDialogCreateTitle
import com.azkar.generated.resources.collectionsDialogDeleteBody
import com.azkar.generated.resources.collectionsDialogDeleteTitle
import com.azkar.generated.resources.collectionsDialogDescriptionCount
import com.azkar.generated.resources.collectionsDialogDescriptionLabel
import com.azkar.generated.resources.collectionsDialogDescriptionPlaceholder
import com.azkar.generated.resources.collectionsDialogDuplicateSubtitle
import com.azkar.generated.resources.collectionsDialogDuplicateTitle
import com.azkar.generated.resources.collectionsDialogEditTitle
import com.azkar.generated.resources.collectionsDialogNameLabel
import com.azkar.generated.resources.collectionsDialogNamePlaceholder
import com.azkar.generated.resources.collectionsDialogNameRequired
import com.azkar.generated.resources.collectionsDialogStepCover
import com.azkar.generated.resources.collectionsDialogStepDetails
import com.azkar.generated.resources.contentPrimaryLanguage
import com.azkar.generated.resources.collectionsDialogStepProgress
import com.azkar.generated.resources.collectionsDialogStepTags
import com.azkar.generated.resources.contentLanguageNote
import com.azkar.generated.resources.contentTranslationAdd
import com.azkar.generated.resources.contentTranslationFor
import com.azkar.generated.resources.contentTranslationRemove
import com.azkar.i18n.displayNameIn
import com.azkar.i18n.isSameLanguageAs
import com.azkar.i18n.textDirectionOf
import com.azkar.ui.components.AzkarDialogSurface
import com.azkar.ui.components.AzkarFilterChip
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.components.AzkarOutlinedField
import com.azkar.ui.components.BorderedStateBox
import com.azkar.ui.components.IconActionButton
import com.azkar.ui.components.PrimaryButton
import com.azkar.ui.components.SecondaryButton
import com.azkar.ui.library.data.CollectionPresenter
import com.azkar.ui.library.model.TagOption
import com.azkar.ui.library.model.TranslationEntry
import com.azkar.ui.library.model.missingLanguages
import com.azkar.ui.library.model.survivingRename
import com.azkar.ui.theme.AzkarColors
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import com.azkar.ui.theme.isRtlLayout
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

/**
 * One page of the editor, in the order the user is walked through them.
 *
 * Split at all because the fields are not one job: a name and a description are what the collection
 * *is*, tags are how the rest of the library finds it, and a cover is decoration that the app will
 * happily invent. Behind one scroll, the first two are competing for the same attention with the third,
 * and the third is the only one most people came to look at.
 *
 * Internal rather than private because [CollectionEditorMode] names its steps in its own declaration,
 * and a private type cannot appear in a public enum's signature.
 */
internal enum class CollectionEditorStep {
    DETAILS,
    TAGS,
    COVER,
    ;

    /**
     * The line under the heading, naming the step rather than the dialog.
     *
     * One per step because a heading that never changes is a heading the eye stops reading. The first
     * sentence a user reads of any form tells them what the form wants; after that it should tell them
     * where they are.
     */
    val subtitleResource: StringResource
        get() =
            when (this) {
                DETAILS -> Res.string.collectionsDialogStepDetails
                TAGS -> Res.string.collectionsDialogStepTags
                COVER -> Res.string.collectionsDialogStepCover
            }
}

/**
 * Which of the three collection dialogs is open, and how many pages it has.
 *
 * They are one dialog with three headings rather than three dialogs because they edit the same fields
 * and submit the same values — the mode only decides the heading, the pages, and what happens on
 * confirm. Splitting them would mean three copies of the same validation.
 */
enum class CollectionEditorMode {
    CREATE,
    EDIT,

    /**
     * A duplicate only takes a name.
     *
     * The copy inherits the description and the tags it was made from, so offering editors for them
     * would suggest the source's own text travels somewhere it does not. It does not inherit the cover
     * either — two collections sharing one image file would make deleting it ambiguous — so there is
     * nothing to edit there but a choice that has not been made yet, and a duplicate is not the moment
     * to make it. Which is also why this is the one mode with a single page: a Next button that walks
     * to a cover step the mode then declines to show would be a step that leads nowhere.
     */
    DUPLICATE,
    ;

    /** Whether this mode edits anything beyond the name. See [DUPLICATE] for why it might not. */
    val editsDetails: Boolean
        get() = this != DUPLICATE

    /**
     * Whether this mode can give the collection a name in more than one language.
     *
     * False only for [DUPLICATE], and for a different reason than [editsDetails]: a duplicate does get
     * a description — the source's — and so translations would be in the same position, except that the
     * mode's whole job is to produce a second copy of a thing the user is *not* looking at. Offering a
     * row of language blocks under a name they just accepted would be asking them to translate a
     * collection they have not read.
     */
    val editsTranslations: Boolean
        get() = this != DUPLICATE

    val titleResource: StringResource
        get() =
            when (this) {
                CREATE -> Res.string.collectionsDialogCreateTitle
                EDIT -> Res.string.collectionsDialogEditTitle
                DUPLICATE -> Res.string.collectionsDialogDuplicateTitle
            }

    /**
     * The pages, in order.
     *
     * A property rather than a hard-coded [CollectionEditorStep] list in the body so that "a duplicate
     * is one page" is stated once, next to the reason for it, instead of being re-derived by whoever
     * next edits the body.
     */
    internal val steps: List<CollectionEditorStep>
        get() =
            when (this) {
                CREATE, EDIT -> ALL_EDITOR_STEPS
                DUPLICATE -> listOf(CollectionEditorStep.DETAILS)
            }

    /**
     * The sentence a single-page editor keeps under its heading.
     *
     * Carried here only so [DUPLICATE] has one. A three-step editor narrates the step it is on instead
     * — see [CollectionEditorStep.subtitleResource] — because a sentence about the whole task stops
     * helping once there are three of the task, and a progress bar on its own says where you are
     * without saying what the place is *for*.
     */
    val singleStepSubtitleResource: StringResource?
        get() =
            when (this) {
                CREATE, EDIT -> null
                DUPLICATE -> Res.string.collectionsDialogDuplicateSubtitle
            }

    val confirmResource: StringResource
        get() =
            when (this) {
                CREATE -> Res.string.collectionsActionCreateCollection
                EDIT, DUPLICATE -> Res.string.collectionsActionSave
            }

    /**
     * The glyph on the confirm button, or none.
     *
     * An arrow only on a create, where the button finishes something and hands back a new tile. On a
     * save it would point at nothing in particular, and the same button wearing two different
     * silhouettes for the same job is a change nobody asked for.
     */
    val confirmLeadingIcon: ImageVector?
        get() = if (this == CREATE) Icons.AutoMirrored.Filled.ArrowForward else null
}

/**
 * The half-typed collection behind [CollectionEditorDialog].
 *
 * Held by the screen rather than by the dialog so dismissing the dialog and reopening it for the same
 * collection starts from what was already typed. A class rather than a data class on purpose:
 * equality would compare two `MutableState` delegates instead of the text the user can see, and the
 * shelf's own `equals` checks would then disagree with what is on screen.
 *
 * The cover is carried as the *name* of a file the app has already copied, for the same reason the
 * column holds a name rather than the path the user picked: the picture belongs to the library now, so
 * moving the home directory cannot break it and deleting the original cannot take it away.
 */
@Stable
class CollectionEditorDraft private constructor(
    val mode: CollectionEditorMode,
    val collectionId: Long,
    initialName: String,
    val nameLocale: Locale,
    initialDescription: String,
    initialTags: List<String>,
    initialCoverImagePath: String?,
    initialTranslations: List<TranslationEntry>,
) {
    var name: String by mutableStateOf(initialName)
        private set

    /**
     * The name this draft was opened with, which is not the name it will be saved under.
     *
     * Held rather than recomputed because the editor prefills its translation boxes from the saved
     * collection, and a rename has to be able to tell a box the user has since rewritten from one they
     * have merely left sitting there. See [submittedTranslations].
     */
    private val openedWith = initialName

    /**
     * The wording this draft was opened with, kept whole because [submittedTranslations] has to be
     * able to tell a box the user rewrote from one they did not.
     */
    private val openedTranslations: List<TranslationEntry> = initialTranslations

    var description: String by mutableStateOf(initialDescription)
        private set

    /**
     * The collection's name and description in every language other than the one [name] is written in.
     *
     * A list of [TranslationEntry] rather than a map because the editor adds languages one at a time
     * and a half-typed one has to have somewhere to live before it has anything to say; a map keyed by
     * locale could hold a blank value just as well, but a list makes "the languages this collection
     * has" the thing being iterated, and that is what the chips and the blocks both ask.
     *
     * Deliberately *not* including the language [name] itself. The base fields are the version in
     * whatever language the user typed them in, which is the app's language in every normal case, and
     * a second copy of the same language would be a field the user fills in only to have it shadowed
     * by the one above it.
     */
    var translations: List<TranslationEntry> by mutableStateOf(initialTranslations)
        private set

    /**
     * The tags this collection carries, in the order they were picked.
     *
     * A list of names rather than the one comma-separated field this replaced, because a list is the
     * only shape that survives both ends of a picker: the chips the user clicks, and the rows storage
     * wants. Order is the picking order rather than alphabetical, because a collection's tags are not
     * a set the user is browsing — they are the three things they decided on, and reshuffling them
     * into a list on every open would move the chip they were aiming at.
     *
     * De-duplicated ignoring case at the point of insertion, by [addTag] and [toggleTag], and not on
     * submit. Case matters here in a way it does not for the name: tags fold to lowercase before they
     * are stored, and the folded name is what the unique index is built on, so "Sleep" and "sleep" are
     * one tag wearing two hats — and a list that offered both would ask storage to insert the same row
     * twice and fail the save. The first spelling the user typed is the one that survives, so their
     * capitalisation is preserved for the chips.
     */
    var tags: List<String> by mutableStateOf(initialTags)
        private set

    /** What the tag picker is filtering by. View state, but it belongs here so the picker stays dumb. */
    var tagQuery: String by mutableStateOf("")
        private set

    /** The half-typed name of a tag that does not exist yet. Cleared by a successful [addTag]. */
    var newTagName: String by mutableStateOf("")
        private set

    /**
     * The name of the cover file, or `null` for the generated wash.
     *
     * Set to `null` to go back to the wash, which is a real choice rather than an absent one — hence a
     * nullable field and not a blank string the storage layer would have to interpret.
     */
    var coverImagePath: String? by mutableStateOf(initialCoverImagePath)
        private set

    /**
     * Whether a save attempt should be rejected.
     *
     * Set only after a save was attempted, so an untouched dialog does not open by scolding the user
     * for a name they have not had a chance to type.
     */
    var nameRejected: Boolean by mutableStateOf(false)
        private set

    /** The name as storage should receive it: trimmed, and required. */
    val submittedName: String get() = name.trim()

    /** The description as storage should receive it: absent rather than a run of spaces. */
    val submittedDescription: String? get() = description.trim().ifEmpty { null }

    /**
     * The tags as storage should receive them.
     *
     * A property rather than a computation because [tags] is already normalised on the way in: every
     * path that changes it goes through [addTag] or [toggleTag]. Re-deriving a list here would imply
     * the draft is trusting its own UI not to put a duplicate in.
     */
    val submittedTagNames: List<String> get() = tags

    /**
     * The translations as storage should receive them, one [LocalizedPair] per language that says
     * something.
     *
     * The blank ones are dropped here rather than refused on the way in because an empty language is
     * the normal state of one someone has just clicked "add a translation" for and has not filled in
     * yet — treating that as an error would put a red note under a field the user has not finished
     * writing. What storage receives is therefore exactly what the user wrote, and a language they
     * abandoned leaves no trace at all.
     *
     * Empty is a legitimate answer and means something: it replaces whatever translations the
     * collection had. Removing the last one through this editor is how a collection goes back to
     * reading in one language.
     */
    val submittedTranslations: Map<Locale, LocalizedPair>
        get() =
            translations
                .survivingRename(
                    renamedFrom = openedWith,
                    renamedTo = name,
                    opened = openedTranslations,
                ).filterNot { it.isBlank }
                .associate { entry ->
                    entry.locale to
                        LocalizedPair(
                            name = entry.name.trim(),
                            description = entry.description?.trim()?.ifEmpty { null },
                        )
                }

    /** Whether [addTag] would do something, which is what the "Add tag" button's enabled state is. */
    val newTagAcceptable: Boolean
        get() {
            val typed = newTagName.trim()
            return typed.isNotEmpty() && !isTagSelected(typed)
        }

    fun updateName(value: String) {
        name = value
    }

    fun updateDescription(value: String) {
        description = value
    }

    fun updateTagQuery(value: String) {
        tagQuery = value
    }

    fun updateNewTagName(value: String) {
        newTagName = value
    }

    /**
     * Records a cover the user has just picked, or clears the existing one when [path] is `null`.
     *
     * The file itself has been copied into the library by the time this is called, so the name here is
     * already durable. See [CollectionEditorDialog]'s `onPickCover` for why the dialog asks rather than
     * reaching for the file itself.
     */
    fun updateCoverImage(path: String?) {
        coverImagePath = path
    }

    /**
     * Whether [locale] already has a version open, which is what decides whether its chip is offered
     * and whether an entry can be added at all.
     *
     * Compared by language rather than by tag so an Arabic entry added as `ar` is not offered a second
     * time as `ar-EG`. Two entries for the same language would resolve to the same row on the way back
     * in, and whichever lost would take the other's text with it.
     */
    fun hasTranslationFor(locale: Locale): Boolean = translations.any { it.locale.isSameLanguageAs(locale) }

    /**
     * Opens a version in [locale], or reports that it did not.
     *
     * Refused rather than silently ignored when [locale] already has one, for the same reason
     * [hasTranslationFor] compares by language: the second entry would replace the first on save, and
     * the user would have typed a version into a field that was never going to keep it.
     */
    fun addTranslation(locale: Locale): Boolean {
        if (hasTranslationFor(locale)) {
            return false
        }
        translations = translations + TranslationEntry(locale = locale)
        return true
    }

    fun updateTranslationName(
        locale: Locale,
        value: String,
    ) {
        translations = translations.map { entry -> entry.replacing(locale) { it.copy(name = value) } }
    }

    fun updateTranslationDescription(
        locale: Locale,
        value: String,
    ) {
        translations = translations.map { entry -> entry.replacing(locale) { it.copy(description = value) } }
    }

    /**
     * Closes the version in [locale].
     *
     * Removes it from the draft rather than blanking it, because those are different outcomes: a blank
     * entry is a version nobody finished, and this is a version the user looked at and decided against.
     * The difference is invisible until they reopen the dialog, which is exactly when it matters.
     */
    fun removeTranslation(locale: Locale) {
        translations = translations.filterNot { it.locale.isSameLanguageAs(locale) }
    }

    /** The entry in [locale] as it stands, for the block that draws it. */
    fun translationFor(locale: Locale): TranslationEntry? =
        translations.firstOrNull { it.locale.isSameLanguageAs(locale) }

    private fun TranslationEntry.replacing(
        locale: Locale,
        change: (TranslationEntry) -> TranslationEntry,
    ): TranslationEntry = if (locale.isSameLanguageAs(this.locale)) change(this) else this

    fun isTagSelected(name: String): Boolean = tags.any { it.equals(name, ignoreCase = true) }

    /** Adds [name] if it is not already carried, drops it if it is. What a chip press does. */
    fun toggleTag(name: String) {
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

    /**
     * Carries a tag the user has just named, and empties the field it was named in.
     *
     * Reports whether it was worth doing, so the caller can leave the panel open on a rejected name —
     * silently swallowing "sleep" because "Sleep" is already there reads as the app having lost it.
     */
    fun addTag(raw: String): Boolean {
        val wanted = raw.trim()
        if (wanted.isEmpty() || isTagSelected(wanted)) {
            return false
        }
        tags = tags + wanted
        newTagName = ""
        return true
    }

    /**
     * The options the picker should draw: everything the library uses, plus anything chosen here that
     * storage has not heard of yet, filtered by [tagQuery].
     *
     * A chosen tag is never filtered out, whether it matches the query or not, because a chip that
     * vanishes the moment someone starts typing is a chip they cannot then take off. The tags chosen
     * here rather than picked from the library are folded in for the same reason: a tag the user has
     * just created has no row anywhere yet, so without this it would have no chip to show as chosen.
     *
     * The query is matched against the label *and* the value, which is what makes the box useful in
     * both directions: a reader typing "morning" finds the chip that reads "أذكار الصباح", and one
     * typing the Arabic finds it too. Matching the label alone would have made the stored names
     * unsearchable once the shipped ones were translated.
     */
    fun tagOptions(available: List<TagOption>): List<TagOption> {
        val unheard =
            tags
                .filter { chosen -> available.none { it.value.equals(chosen, ignoreCase = true) } }
                .map(TagOption::of)
        val options = (available + unheard).distinctBy { it.value.lowercase() }
        val query = tagQuery.trim()
        if (query.isEmpty()) {
            return options
        }
        return options.filter { it.matches(query) || isTagSelected(it.value) }
    }

    /**
     * Whether a query could be looking for this tag.
     *
     * Both names because [label] is what the user can read and [value] is what the tag is called in
     * the rows this picker saves against; a reader who knows the tag by either should find it.
     */
    private fun TagOption.matches(query: String): Boolean =
        label.contains(query, ignoreCase = true) || value.contains(query, ignoreCase = true)

    /** The confirm button's click handler. Reports whether the dialog may close. */
    fun submit(): Boolean {
        if (submittedName.isEmpty()) {
            nameRejected = true
            return false
        }
        return true
    }

    companion object {
        /** Only read for [CollectionEditorMode.EDIT] and [CollectionEditorMode.DUPLICATE]. */
        const val NO_COLLECTION_ID = -1L

        fun create(nameLocale: Locale = Locale.ENGLISH): CollectionEditorDraft =
            CollectionEditorDraft(
                mode = CollectionEditorMode.CREATE,
                collectionId = NO_COLLECTION_ID,
                initialName = "",
                nameLocale = nameLocale,
                initialDescription = "",
                initialTags = emptyList(),
                initialCoverImagePath = null,
                initialTranslations = emptyList(),
            )

        /**
         * The editor for a collection that already exists.
         *
         * Called `edit` rather than `rename` because it opens every field the collection has, not just
         * the name — and a method named for the one field it used to expose is how a dialog ends up
         * called "Rename Collection" while silently offering a description, tags and a cover.
         */
        fun edit(collection: RemembranceCollection): CollectionEditorDraft =
            CollectionEditorDraft(
                mode = CollectionEditorMode.EDIT,
                collectionId = requireId(collection),
                initialName = collection.name,
                nameLocale = collection.nameLocale,
                initialDescription = collection.description.orEmpty(),
                // Sorted rather than in the set's own order for the same reason the chips are: a
                // hash-ordered set would reorder the chips every time the user opened the dialog.
                initialTags =
                    collection.tags
                        .map { it.name }
                        .sortedWith(String.CASE_INSENSITIVE_ORDER),
                initialCoverImagePath = collection.coverImagePath,
                initialTranslations = collection.entries(),
            )

        /**
         * @param newName the name already carrying its "(copy)" suffix. Resolved by the caller so the
         * suffix is translated in the language on screen right now, not the one the draft remembers.
         */
        fun duplicate(
            collection: RemembranceCollection,
            newName: String,
            nameLocale: Locale = Locale.ENGLISH,
        ): CollectionEditorDraft =
            CollectionEditorDraft(
                mode = CollectionEditorMode.DUPLICATE,
                collectionId = requireId(collection),
                initialName = newName,
                nameLocale = nameLocale,
                initialDescription = "",
                initialTags = emptyList(),
                initialCoverImagePath = null,
                // Empty even though the source has some. A duplicate is a new collection with a new
                // name, and inheriting the source's other languages would leave translations that
                // describe a passage the user is not editing and cannot see.
                initialTranslations = emptyList(),
            )

        private fun requireId(collection: RemembranceCollection): Long =
            requireNotNull(collection.id) { "A persisted collection came back without an identifier" }
    }
}

/**
 * The collection's translations as editable entries, in a fixed order.
 *
 * Sorted by language rather than kept in the map's iteration order, because two opens of the same
 * collection should draw the same blocks in the same places — a set ordering that changed between
 * opens would put the Arabic version above the French one one day and below it the next. Blanks are
 * skipped: storage should not be holding one, but a translation with no name has nothing to show
 * and no way to be corrected from here.
 */
private fun RemembranceCollection.entries(): List<TranslationEntry> =
    translations.entries
        .filter { pair -> pair.value.name.isNotBlank() }
        .sortedBy { it.key.language }
        .map { (locale, pair) -> TranslationEntry(locale = locale, name = pair.name, description = pair.description) }

/**
 * Everything the editor needs from the world around it, as one value.
 *
 * Bundled rather than spread across the dialog's parameters for the same reason the dialog is one
 * composable for three modes: these three are not choices the caller makes independently, they are
 * one screen's worth of surroundings. Which tag vocabulary it shows and which cover it draws from are
 * the same catalogue, and passing them as separate arguments invited call sites that passed one
 * derivation and forgot the other.
 */
@Immutable
internal class CollectionEditorEnvironment(
    /** Decoded cover pictures, so a cover already chosen previews itself. `null` before storage is up. */
    val covers: CollectionCoverCache?,
    /** Every tag the library uses, offered as chips. */
    val availableTags: List<TagOption>,
    /**
     * Opens the file chooser, captioned with the [title] it is given, and returns the name of the copy
     * the app made, or `null` if the user backed out. A parameter rather than a file dialog this file
     * opens itself for two reasons: a chooser is a blocking desktop call that has no business being
     * reached for from inside a composition, and the copy is a write that belongs to the gateway beside
     * every other write. A `null` return means "cancelled", which is why it is not folded into the same
     * value as an error.
     *
     * The title arrives as text because the chooser is a window this app never composes: it has its own
     * title bar, so a `StringResource` cannot be read wherever the dialog is opened and a resource would
     * have to be resolved before the call anyway. Passing it in is what keeps it in the language the
     * user is looking at, which a name resolved down in the gateway could not be.
     */
    val onPickCover: suspend (title: String) -> String?,
)

/**
 * The editor, walked one [CollectionEditorStep] at a time.
 *
 * One dialog for creating, editing and duplicating, because the three differ only in what they are
 * called, which pages they offer, and what happens on confirm. The header and the footer do not
 * scroll and the step does: the header names where the user is, and the footer carries the button
 * that moves them on, so a body that scrolled either of them away would make them hunt for the
 * control they were already looking at.
 *
 * The page lives here rather than in [CollectionEditorDraft] because it is the one piece of editor
 * state that the draft does not need to remember: reopening the editor starts at the first page for
 * the same reason it starts with the name in the field — that is what coming to this dialog means.
 */
@Composable
internal fun CollectionEditorDialog(
    draft: CollectionEditorDraft,
    environment: CollectionEditorEnvironment,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        description: String?,
        tagNames: List<String>,
        coverImagePath: String?,
        translations: Map<Locale, LocalizedPair>,
    ) -> Unit,
) {
    val colors = AzkarTheme.colors
    val steps = draft.mode.steps
    var page by rememberSaveable { mutableStateOf(0) }
    val step = steps[page.coerceIn(steps.indices)]
    Dialog(onDismissRequest = onDismiss) {
        AzkarDialogSurface(modifier = Modifier.widthIn(min = DIALOG_MIN_WIDTH, max = DIALOG_MAX_WIDTH)) {
            Column {
                CollectionEditorHeader(
                    mode = draft.mode,
                    step = step,
                    // A single-page editor has nothing to narrate, so it keeps the sentence about the
                    // task rather than borrowing one of the per-step sentences.
                    stepCount = steps.size,
                    onDismiss = onDismiss,
                )
                if (steps.size > 1) {
                    Spacer(Modifier.height(HEADER_STEP_GAP))
                    CollectionStepBar(steps = steps, current = steps.indexOf(step))
                }
                Spacer(Modifier.height(BLOCK_GAP))
                HorizontalDivider(color = colors.border)
                Spacer(Modifier.height(BLOCK_GAP))
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = DIALOG_BODY_MAX_HEIGHT)
                            .verticalScroll(rememberScrollState()),
                ) {
                    CollectionEditorStepBody(
                        draft = draft,
                        step = step,
                        environment = environment,
                    )
                }
                Spacer(Modifier.height(BLOCK_GAP))
                HorizontalDivider(color = colors.border)
                Spacer(Modifier.height(BLOCK_GAP))
                CollectionEditorActions(
                    draft = draft,
                    step = step,
                    lastStep = step == steps.last(),
                    navigation =
                        editorNavigation(
                            draft = draft,
                            onBack = { page -= 1 },
                            onNext = { page += 1 },
                            onDismiss = onDismiss,
                            onConfirm = onConfirm,
                        ),
                )
            }
        }
    }
}

/**
 * The four arrows, with the one rule the editor has about the name attached to both ways forward.
 *
 * Both the Next and the confirm path run [CollectionEditorDraft.submit] first. Next needs it because
 * the first page is the only place the name is asked for and every later page stands on it; confirm
 * needs it because a draft can arrive with an empty name from a mode that never walked the pages.
 * Sharing the check rather than repeating it is what stops the two paths from drifting into one that
 * reports a rejected name and one that quietly creates a nameless collection.
 */
private fun editorNavigation(
    draft: CollectionEditorDraft,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        description: String?,
        tagNames: List<String>,
        coverImagePath: String?,
        translations: Map<Locale, LocalizedPair>,
    ) -> Unit,
) = CollectionEditorNavigation(
    onBack = onBack,
    onNext = { if (draft.submit()) onNext() },
    onDismiss = onDismiss,
    onConfirm = {
        if (draft.submit()) {
            onConfirm(
                draft.submittedName,
                draft.submittedDescription,
                draft.submittedTagNames,
                draft.coverImagePath,
                draft.submittedTranslations,
            )
        }
    },
)

/**
 * The one page the editor is showing.
 *
 * A `when` over the step rather than three dialogs or three conditional screens: the fields are the
 * same composables the single-page editor used, and what changed is which of them are on screen. A
 * duplicate stops after the first branch and never reaches the pages it has no fields for.
 */
@Composable
private fun CollectionEditorStepBody(
    draft: CollectionEditorDraft,
    step: CollectionEditorStep,
    environment: CollectionEditorEnvironment,
) {
    when (step) {
        CollectionEditorStep.DETAILS -> {
            CollectionNameField(draft = draft)
            if (draft.mode.editsDetails) {
                Spacer(Modifier.height(FIELD_GAP))
                CollectionDescriptionField(draft = draft)
            }
            if (draft.mode.editsTranslations) {
                Spacer(Modifier.height(FIELD_GAP))
                CollectionTranslationSection(draft = draft)
            }
        }

        CollectionEditorStep.TAGS ->
            CollectionTagPickerPanel(
                draft = draft,
                availableTags = environment.availableTags,
            )

        CollectionEditorStep.COVER ->
            CoverEditorSection(
                draft = draft,
                covers = environment.covers,
                onPickCover = environment.onPickCover,
            )
    }
}

/**
 * How far along the walk the user is, as a bar across the full width.
 *
 * A bar rather than dots, because three dots are read as decoration and a bar is read as a quantity,
 * which is the thing being asked. Every segment up to and including the current one is filled, so the
 * bar answers "how much is left" without the user having to count, and a filled final segment is the
 * only cue that pressing the primary button now *finishes* rather than advances.
 *
 * It is not clickable. A progress bar that looks like a tab strip and then refuses to jump is worse
 * than one that plainly does not move — jumping would also skip the check that the name was filled in
 * before the pages that depend on it.
 */
@Composable
private fun CollectionStepBar(
    steps: List<CollectionEditorStep>,
    current: Int,
) {
    val colors = AzkarTheme.colors
    // Resolved before the modifier: the semantics block is a plain lambda, not a composable one, so
    // a `stringResource` call inside it does not compile.
    val progress =
        stringResource(Res.string.collectionsDialogStepProgress, current + 1, steps.size)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { contentDescription = progress },
        horizontalArrangement = Arrangement.spacedBy(STEP_BAR_GAP),
    ) {
        steps.forEachIndexed { index, _ ->
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(STEP_BAR_HEIGHT)
                        .clip(RoundedCornerShape(STEP_BAR_RADIUS))
                        .background(if (index <= current) colors.primary else colors.border),
            )
        }
    }
}

/**
 * The cover panel, plus the one piece of state only it needs: whether a pick is in flight.
 *
 * The state lives here rather than in the dialog so that closing and reopening the picker is not a
 * thing the rest of the form has to be told about, and so that the dialog's own body is a list of fields
 * rather than a list of fields with a coroutine wedged between two of them.
 */
@Composable
private fun CoverEditorSection(
    draft: CollectionEditorDraft,
    covers: CollectionCoverCache?,
    onPickCover: suspend (title: String) -> String?,
) {
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    // Read here, where the current locale is the one on screen, rather than stored on the draft or the
    // environment: both outlive a language switch, and a chooser that opened captioned in the language
    // the app started in would be the one window in the app that does not follow the setting.
    val pickerTitle = stringResource(Res.string.collectionsDialogChooseCoverTitle)
    CoverEditorPanel(
        draft = draft,
        covers = covers,
        picking = picking,
        onPick = {
            // A re-entrancy guard rather than a read of the button's own disabled state: the chooser
            // blocks, so a second press arrives while the first is still inside it, and `enabled` has
            // not necessarily been recomposed yet.
            if (picking) {
                return@CoverEditorPanel
            }
            scope.launch {
                picking = true
                // Cleared even if the copy throws, so a failed pick cannot leave the button permanently
                // disabled for the rest of the dialog's life.
                try {
                    onPickCover(pickerTitle)?.let(draft::updateCoverImage)
                } finally {
                    picking = false
                }
            }
        },
    )
}

/**
 * The badge, the heading, the line that says which page this is, and the way out.
 *
 * All of the heading's parts are here rather than bare text on the surface because a dialog with
 * no icon reads as a browser alert and a dialog with no close button reads as one on this machine
 * forever. The badge carries the brand gradient rather than a flat tint because that tile is the only
 * place in a dialog where the app gets to say what it is, and a flat square of `primary` would look
 * like another selected control.
 *
 * [stepCount] decides what the second line says rather than the step alone, because a one-page editor
 * showing "What to call it, and what it is for" would be captioning a sentence the user can already
 * see in the field below it.
 *
 * The close button is an [IconActionButton] rather than a bare `IconButton` so it collects the same
 * hover fill as every other icon control in the app — a pointer arriving on it should show something.
 */
@Composable
private fun CollectionEditorHeader(
    mode: CollectionEditorMode,
    step: CollectionEditorStep,
    stepCount: Int,
    onDismiss: () -> Unit,
) {
    val colors = AzkarTheme.colors
    val subtitle = if (stepCount > 1) step.subtitleResource else mode.singleStepSubtitleResource
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(HEADER_BADGE)
                    .clip(RoundedCornerShape(HEADER_BADGE_RADIUS))
                    .background(Brush.linearGradient(listOf(colors.brandTileStart, colors.brandTileEnd))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AzkarIcons.BookOpen,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(HEADER_BADGE_GLYPH),
            )
        }
        Spacer(Modifier.width(HEADER_GAP))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(mode.titleResource),
                color = colors.textPrimary,
                fontSize = HEADER_TITLE_SP,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(HEADER_SUBTITLE_GAP))
                Text(
                    text = stringResource(subtitle),
                    color = colors.textSecondary,
                    fontSize = HEADER_SUBTITLE_SP,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(HEADER_GAP))
        IconActionButton(
            icon = Icons.Filled.Close,
            contentDescription = stringResource(Res.string.collectionsDialogClose),
            onClick = onDismiss,
            iconSize = HEADER_CLOSE_GLYPH,
            tint = colors.textSecondary,
        )
    }
}

@Composable
private fun CollectionNameField(draft: CollectionEditorDraft) {
    val colors = AzkarTheme.colors
    // The ambient direction, used only while the field is still empty — see [textDirectionOf].
    val ambient = if (isRtlLayout()) LayoutDirection.Rtl else LayoutDirection.Ltr
    FieldLabel(text = stringResource(Res.string.collectionsDialogNameLabel), required = true)
    Text(
        text = stringResource(
            Res.string.contentPrimaryLanguage,
            draft.nameLocale.displayNameIn(AzkarTheme.locale),
        ),
        color = colors.textSecondary,
        fontSize = HINT_SP,
    )
    Spacer(Modifier.height(SMALL_GAP))
    AzkarOutlinedField(
        value = draft.name,
        onValueChange = draft::updateName,
        placeholder = stringResource(Res.string.collectionsDialogNamePlaceholder),
        modifier = Modifier.fillMaxWidth(),
        // What the user is about to type decides the direction, not what the app is written in.
        textDirection = textDirectionOf(draft.name, ambient),
    )
    if (draft.nameRejected) {
        Text(
            text = stringResource(Res.string.collectionsDialogNameRequired),
            color = colors.dialogValidation,
            fontSize = VALIDATION_SP,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = VALIDATION_GAP),
        )
    }
}

/**
 * The description, and the count of what is left of it.
 *
 * The counter is drawn inside the field's own bottom corner rather than beneath it, which is the only
 * place it can live without pretending to be part of the text: it is anchored to the box, so it does
 * not move as the field grows, and a count that sat under the field would be read as a caption — the
 * one thing a character counter must not look like.
 *
 * Material's trailing slot cannot be used for it. That slot is laid out at the *vertical centre* of a
 * multi-line field, which would hang the count beside the first line while the text it counts ran on
 * underneath it, and the field offers no way to set its content padding from outside. So the field is
 * given a height and a line limit it will never outgrow — [DESCRIPTION_LINES] lines inside
 * [DESCRIPTION_FIELD_HEIGHT] leaves a band at the bottom that no keystroke can reach — and the counter
 * is anchored into that band. A limit rather than a taller box, because a taller box with unbounded
 * lines is a box the text will eventually fill, and a counter under the last line is a counter lying.
 */
@Composable
private fun CollectionDescriptionField(draft: CollectionEditorDraft) {
    val colors = AzkarTheme.colors
    val ambient = if (isRtlLayout()) LayoutDirection.Rtl else LayoutDirection.Ltr
    FieldLabel(text = stringResource(Res.string.collectionsDialogDescriptionLabel))
    Box(modifier = Modifier.fillMaxWidth()) {
        AzkarOutlinedField(
            value = draft.description,
            // Truncated rather than refused: a field that silently stops accepting keystrokes is a
            // field that has stopped working, and the counter says why the tail is missing.
            onValueChange = { draft.updateDescription(it.take(DESCRIPTION_LIMIT)) },
            placeholder = stringResource(Res.string.collectionsDialogDescriptionPlaceholder),
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minHeight = DESCRIPTION_FIELD_HEIGHT,
            maxLines = DESCRIPTION_LINES,
            textDirection = textDirectionOf(draft.description, ambient),
        )
        Text(
            text =
                stringResource(
                    Res.string.collectionsDialogDescriptionCount,
                    draft.description.length,
                    DESCRIPTION_LIMIT,
                ),
            color = colors.textSecondary,
            fontSize = DESCRIPTION_COUNT_SP,
            maxLines = 1,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = DESCRIPTION_COUNT_INSET, bottom = DESCRIPTION_COUNT_BOTTOM_INSET),
        )
    }
}

/**
 * The other languages this collection can be named in.
 *
 * A collapsed row of language chips until one is pressed, then a block per language. The alternative —
 * a field per shipped language, always — would put two boxes in front of every user to say that one of
 * them, usually, says nothing they need, and a form whose optional parts are all visible teaches people
 * to ignore them.
 *
 * The languages offered are the shipped ones minus the two this panel cannot add: the language the name
 * above is already written in — which is *not* the language the screen is in, once the interface has
 * been switched under a name that stayed put — and any language that already has a block, because there
 * is one field per language by design. Both exclusions are made here rather than in the draft so the
 * draft still refuses a duplicate — the UI hiding a chip is a convenience, not a guarantee.
 *
 * The note above it is the honest half of this: naming a collection in one language does not translate
 * it, and without saying so the first thing a user discovers is that their Arabic collection read "Sleep"
 * in the Arabic interface. It is drawn once, for the base fields, rather than per block — the blocks
 * answer their own question by existing.
 */
@Composable
private fun CollectionTranslationSection(draft: CollectionEditorDraft) {
    val locale = AzkarTheme.locale
    // What is already written, not what the screen is in — see [missingLanguages]. The description is
    // consulted second so a name that is blank but a sentence that is not still counts.
    val offered =
        missingLanguages(
            name = draft.name.ifBlank { draft.description },
            existing = draft.translations,
            uiLocale = locale,
            nameLocale = draft.nameLocale,
        )
    Column(modifier = Modifier.fillMaxWidth()) {
        FieldLabel(text = stringResource(Res.string.contentTranslationAdd))
        Text(
            text = stringResource(Res.string.contentLanguageNote),
            color = AzkarTheme.colors.textSecondary,
            fontSize = HINT_SP,
            modifier = Modifier.padding(top = LABEL_GAP),
        )
        if (offered.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = BLOCK_GAP),
                horizontalArrangement = Arrangement.spacedBy(TRANSLATION_CHIP_GAP),
            ) {
                offered.forEach { candidate ->
                    AzkarFilterChip(
                        text = candidate.displayNameIn(locale),
                        selected = false,
                        onClick = { draft.addTranslation(candidate) },
                        leadingIcon = Icons.Filled.Add,
                    )
                }
            }
        }
        draft.translations.forEach { entry ->
            Spacer(Modifier.height(TRANSLATION_BLOCK_GAP))
            CollectionTranslationBlock(draft = draft, entry = entry)
        }
    }
}

/**
 * One language's name and description, with the button that takes it away.
 *
 * Titled by the language rather than by "Translation", because a list of identically-titled boxes is a
 * list the user has to read the contents of to tell apart, and the one thing they need to tell apart
 * first is which language each box is for. The endonym is what makes that readable to someone who does
 * not read the interface language — the same reason the language switcher names its options by
 * endonym, and for the same reason it is not translated here: "العربية" is the word for Arabic in every
 * language the app ships, while a translated label would only be readable to people who already read
 * the interface.
 *
 * Both fields point their direction at what has been typed, for the reason [CollectionNameField]
 * does. A translation is the one place in this app where the text is *certainly* in another language,
 * so the hint is never going to be a guess the caller has to make.
 */
@Composable
private fun CollectionTranslationBlock(
    draft: CollectionEditorDraft,
    entry: TranslationEntry,
) {
    val locale = AzkarTheme.locale
    val ambient = if (isRtlLayout()) LayoutDirection.Rtl else LayoutDirection.Ltr
    val spoken = entry.locale.displayNameIn(locale)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.contentTranslationFor, spoken),
                color = AzkarTheme.colors.textPrimary,
                fontSize = LABEL_SP,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconActionButton(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(Res.string.contentTranslationRemove),
                onClick = { draft.removeTranslation(entry.locale) },
                iconSize = TRANSLATION_REMOVE_GLYPH,
                tint = AzkarTheme.colors.textSecondary,
            )
        }
        AzkarOutlinedField(
            value = entry.name,
            onValueChange = { draft.updateTranslationName(entry.locale, it) },
            placeholder = spoken,
            modifier = Modifier.fillMaxWidth().padding(top = LABEL_GAP),
            textDirection = textDirectionOf(entry.name, ambient),
        )
        AzkarOutlinedField(
            value = entry.description.orEmpty(),
            onValueChange = { draft.updateTranslationDescription(entry.locale, it.take(DESCRIPTION_LIMIT)) },
            placeholder = stringResource(Res.string.collectionsDialogDescriptionPlaceholder),
            modifier = Modifier.fillMaxWidth().padding(top = LABEL_GAP),
            singleLine = false,
            minHeight = DESCRIPTION_FIELD_HEIGHT,
            maxLines = DESCRIPTION_LINES,
            textDirection = textDirectionOf(entry.description, ambient),
        )
    }
}

/**
 * The cover as it currently stands, in a box of its own.
 *
 * A panel rather than a bare row because a cover is a *choice* among others and the three facts about
 * it — what it looks like, whether there is one, and how to change it — need to read as one object.
 * Left as a row in a column of bare fields it read as a fourth field with a button on it.
 *
 * The preview is [CollectionCoverArt] rather than a plain image so that "no cover" and "a cover whose
 * file has since been deleted" render as the same thing the card will render as. Showing a broken
 * thumbnail here and a generated wash on the shelf would make a working collection look broken and a
 * broken one look fixed, which are both the opposite of what the user needs to see.
 *
 * Remove is offered only once there is something to remove: a button that appears and does nothing is
 * worse than no button, and the panel already says plainly when the cover is the generated wash.
 */
@Composable
private fun CoverEditorPanel(
    draft: CollectionEditorDraft,
    covers: CollectionCoverCache?,
    picking: Boolean,
    onPick: () -> Unit,
) {
    FieldLabel(text = stringResource(Res.string.collectionsDialogCoverLabel))
    BorderedStateBox(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CollectionCoverArt(
                iconKey = draft.coverIconKey(),
                coverImagePath = draft.coverImagePath,
                covers = covers,
                modifier = Modifier.size(COVER_PREVIEW_SIZE).clip(RoundedCornerShape(COVER_PREVIEW_RADIUS)),
                iconSize = COVER_PREVIEW_GLYPH,
            )
            Spacer(Modifier.width(COVER_GAP))
            Column(modifier = Modifier.weight(1f)) {
                CoverStatusLines(chosen = draft.coverImagePath != null)
                Spacer(Modifier.height(COVER_BUTTON_GAP))
                SecondaryButton(
                    text =
                        if (picking) {
                            stringResource(Res.string.collectionsDialogCoverBusy)
                        } else {
                            stringResource(Res.string.collectionsActionChooseCover)
                        },
                    onClick = onPick,
                    enabled = !picking,
                    leadingIcon = Icons.Outlined.Image,
                )
                if (draft.coverImagePath != null) {
                    Spacer(Modifier.height(COVER_REMOVE_GAP))
                    SecondaryButton(
                        text = stringResource(Res.string.collectionsActionRemoveCover),
                        onClick = { draft.updateCoverImage(null) },
                    )
                }
            }
        }
    }
}

/**
 * The two lines beside the preview: what the cover is, and what happens if it is left alone.
 *
 * The second line is not decoration. "No cover selected" on its own is a dead end that leaves the
 * reader assuming something is missing, and saying the app will draw one turns the absence into a
 * decision the user already made by doing nothing.
 */
@Composable
private fun CoverStatusLines(chosen: Boolean) {
    val colors = AzkarTheme.colors
    Text(
        text =
            if (chosen) {
                stringResource(Res.string.collectionsDialogCoverChosenTitle)
            } else {
                stringResource(Res.string.collectionsDialogCoverNoneTitle)
            },
        color = colors.textPrimary,
        fontSize = COVER_STATUS_SP,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(COVER_TEXT_GAP))
    Text(
        text =
            if (chosen) {
                stringResource(Res.string.collectionsDialogCoverChosenBody)
            } else {
                stringResource(Res.string.collectionsDialogCoverNoneBody)
            },
        color = colors.textSecondary,
        fontSize = COVER_HINT_SP,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The glyph the preview shows when there is no cover, taken from the same derivation the shelf uses.
 *
 * Reads raw label names rather than tags because the shelf's [CollectionPresenter.iconKeyForTags]
 * takes a loaded [RemembranceCollection]'s tags and this dialog has only what it was told — a name and
 * a list of labels for a collection that does not exist yet. Both paths sort the labels, take the first
 * and fall back to the same key, so they agree by construction.
 */
private fun CollectionEditorDraft.coverIconKey(): String = CollectionPresenter.iconKeyForTagNames(submittedTagNames)

/**
 * The delete confirmation.
 *
 * Names the collection rather than asking about "this collection": on a shelf of a dozen tiles a
 * generic question is ambiguous, and by the time the answer turns out to be wrong the collection is
 * already gone. It also says what is *not* being deleted, because losing remembrances to a cleared
 * shelf is the failure this sentence exists to prevent.
 */
@Composable
internal fun ConfirmCollectionDeleteDialog(
    name: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = AzkarTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        AzkarDialogSurface(modifier = Modifier.widthIn(min = 340.dp, max = 460.dp)) {
            Column {
                Text(
                    text = stringResource(Res.string.collectionsDialogDeleteTitle),
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(Res.string.collectionsDialogDeleteBody, name),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(18.dp))
                DialogActions(
                    confirmLabel = stringResource(Res.string.collectionsActionDelete),
                    onDismiss = onDismiss,
                    onConfirm = onConfirm,
                )
            }
        }
    }
}

/**
 * A caption above a field.
 *
 * The field itself takes a placeholder, which vanishes the moment there is text in it — so the label
 * has to be the thing that stays, or a filled-in field has no name at all.
 *
 * [required] draws the red asterisk rather than spelling "required" out, and the red is
 * [AzkarColors.dialogValidation] — the same token the "give it a name" note uses. A second red for the
 * same meaning would leave someone wondering which of the two is the real error.
 *
 * Internal rather than private because the tag picker in `CollectionTagPicker.kt` captions a panel with
 * it rather than with a field's own label, and a second copy of five lines of type settings would
 * inevitably drift from this one.
 */
@Composable
internal fun FieldLabel(
    text: String,
    required: Boolean = false,
) {
    val colors = AzkarTheme.colors
    Row(
        modifier = Modifier.padding(bottom = LABEL_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = colors.textPrimary,
            fontSize = LABEL_SP,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (required) {
            Text(
                text = REQUIRED_MARK,
                color = colors.dialogValidation,
                fontSize = LABEL_SP,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = REQUIRED_MARK_GAP),
            )
        }
    }
}

/** Cancel on the left, confirm on the right — the order every other dialog in the app uses. */
@Composable
private fun DialogActions(
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLeadingIcon: ImageVector? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryButton(text = stringResource(Res.string.collectionsActionCancel), onClick = onDismiss)
        Spacer(Modifier.width(FIELD_GAP))
        PrimaryButton(
            text = confirmLabel,
            onClick = onConfirm,
            leadingIcon = confirmLeadingIcon,
        )
    }
}

/**
 * The editor's own footer: Cancel, then either "back and on" or "back and finish".
 *
 * Cancel stays on the left on every page rather than becoming Back once there is somewhere to go back
 * to. The wizard is a dialog, and a dialog that can only be left by walking backwards out of it is a
 * dialog that traps anyone who opened it by accident — while a Back button that only exists on pages
 * two and three is a control whose position moves under the pointer.
 *
 * "Next" is the primary button on every page but the last, and it wears the same arrow the create
 * button wears, because it is the same promise: something will exist that did not before. On the last
 * page the label and the glyph both come from the mode, which is the only place that knows whether the
 * thing being made is a new collection or an edit to an existing one.
 *
 * The pages carry their state in the draft, so Next never validates anything except on the first page
 * — where the name is asked for, and where [CollectionEditorDraft.submit] is the only check in the
 * editor that can refuse.
 */
@Composable
private fun CollectionEditorActions(
    draft: CollectionEditorDraft,
    step: CollectionEditorStep,
    lastStep: Boolean,
    navigation: CollectionEditorNavigation,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryButton(
            text = stringResource(Res.string.collectionsActionCancel),
            onClick = navigation.onDismiss,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step != draft.mode.steps.first()) {
                SecondaryButton(
                    text = stringResource(Res.string.collectionsActionStepBack),
                    onClick = navigation.onBack,
                )
                Spacer(Modifier.width(FIELD_GAP))
            }
            PrimaryButton(
                text =
                    if (lastStep) {
                        stringResource(draft.mode.confirmResource)
                    } else {
                        stringResource(Res.string.collectionsActionNext)
                    },
                onClick = if (lastStep) navigation.onConfirm else navigation.onNext,
                leadingIcon =
                    if (lastStep) {
                        draft.mode.confirmLeadingIcon
                    } else {
                        Icons.AutoMirrored.Filled.ArrowForward
                    },
            )
        }
    }
}

/**
 * What the editor's four buttons do, gathered so [CollectionEditorActions] can say what it is doing
 * without a seven-argument signature.
 *
 * Built inside the dialog rather than passed in, because all four arrows point at the dialog's own
 * state — the page counter and the draft — and none of them is a decision the caller makes. What the
 * caller does decide, [onConfirm][CollectionEditorNavigation.onConfirm]'s effect, is already the
 * dialog's `onConfirm` parameter.
 */
@Immutable
internal class CollectionEditorNavigation(
    val onBack: () -> Unit,
    val onNext: () -> Unit,
    val onDismiss: () -> Unit,
    val onConfirm: () -> Unit,
)

/**
 * The three pages of the editor, each on its own.
 *
 * Three previews rather than one with a note, because the point of splitting the form is that each
 * page is a different layout with a different height — a single preview of the first page is exactly
 * the preview that would have hidden the problem.
 */
@Preview
@Composable
private fun CollectionEditorDetailsPreview() {
    AzkarThemePreview(width = 640.dp, height = 640.dp) {
        val draft = CollectionEditorDraft.create()
        draft.updateName("Morning Azkar")
        draft.updateDescription("The remembrances said before the sun is up.")
        CollectionEditorDialog(
            draft = draft,
            environment = previewEditorEnvironment(),
            onDismiss = {},
            onConfirm = { _, _, _, _, _ -> },
        )
    }
}

@Preview
@Composable
private fun CollectionEditorTagsPreview() {
    AzkarThemePreview(width = 640.dp, height = 640.dp) {
        val draft = CollectionEditorDraft.create()
        draft.toggleTag("morning")
        draft.toggleTag("sleep")
        CollectionEditorDialog(
            draft = draft,
            environment = previewEditorEnvironment(),
            onDismiss = {},
            onConfirm = { _, _, _, _, _ -> },
        )
    }
}

@Preview
@Composable
private fun CollectionEditorCoverPreview() {
    AzkarThemePreview(width = 640.dp, height = 640.dp) {
        CollectionEditorDialog(
            draft = CollectionEditorDraft.create(),
            environment = previewEditorEnvironment(),
            onDismiss = {},
            onConfirm = { _, _, _, _, _ -> },
        )
    }
}

/**
 * The surroundings the previews share.
 *
 * A function rather than a constant because the environment holds a suspending lambda, and a
 * `val` holding one at file scope would be a coroutine started by a class loader.
 */
private fun previewEditorEnvironment() =
    CollectionEditorEnvironment(
        covers = null,
        availableTags = listOf("morning", "sleep", "travel", "after prayer").map(TagOption::of),
        onPickCover = { null },
    )

@Preview
@Composable
private fun ConfirmCollectionDeleteDialogPreview() {
    AzkarThemePreview(width = 520.dp) {
        ConfirmCollectionDeleteDialog(name = "Morning Azkar", onDismiss = {}, onConfirm = {})
    }
}

// ---- plumbing -------------------------------------------------------------------------------------

/**
 * The pages of the editor, for the modes that walk through all of them.
 *
 * A single shared list rather than `values().toList()` per mode, so that adding a
 * [CollectionEditorStep] to the enum adds it to every multi-page mode and cannot be half-remembered.
 * A file-level `val` rather than a member of the enum because an enum member would have to be a
 * function returning the list, and an enum whose constants cannot hold data cannot have a shared one.
 */
private val ALL_EDITOR_STEPS =
    listOf(CollectionEditorStep.DETAILS, CollectionEditorStep.TAGS, CollectionEditorStep.COVER)

/**
 * The width the dialog asks for.
 *
 * Wide enough for the tag picker's chips to wrap into at least two rows rather than one long ragged
 * line, and narrow enough that the tag panel does not become the widest thing on screen. A dialog
 * sized to its content would settle at the *field* width on a short draft and spring to the panel's
 * width the moment someone typed a tag, which is the sort of reflow that reads as a bug.
 */
private val DIALOG_MIN_WIDTH = 440.dp

private val DIALOG_MAX_WIDTH = 600.dp

/**
 * How much of the dialog one page may take before it starts to scroll.
 *
 * Comfortably more than the tallest page — the tag panel, once a library has a few dozen labels — and
 * far less than the 460dp the single-page form needed. That number was set so the cover panel was not
 * pushed off the bottom by a long description; with the description on its own page there is nothing
 * left to push it, and a cap chosen for the worst case is a cap every page pays for in the form of a
 * dialog that is taller than it has anything to show.
 */
private val DIALOG_BODY_MAX_HEIGHT = 320.dp

/** One gap for everything that a divider does not already separate. */
private val BLOCK_GAP = 16.dp

/** Between two fields that have no panel around them to separate them. */
private val FIELD_GAP = 14.dp

/** The space a label takes from the field it names. */
private val LABEL_GAP = 5.dp

/**
 * The asterisk, and the hair of space in front of it.
 *
 * It hugs the label rather than floating after a gap because a required marker is punctuation, not a
 * second word, and anything wider than a hair makes the label read as "Collection name *" — two
 * things, with a choice between them.
 */
private const val REQUIRED_MARK: String = "*"

private val REQUIRED_MARK_GAP = 2.dp

private val LABEL_SP = 12.sp

/** Small enough to sit under a field as an apology rather than as a second heading. */
private val VALIDATION_SP = 11.sp

/** Between a field that was rejected and the sentence saying why. */
private val VALIDATION_GAP = 5.dp

/** Between the heading and the sentence under it, inside the cover panel. */
private val COVER_TEXT_GAP = 2.dp

/** Between the cover panel's sentence and the first button under it. */
private val COVER_BUTTON_GAP = 10.dp

/** Between the "Choose image…" button and the "Remove" button under it. */
private val COVER_REMOVE_GAP = 6.dp

/**
 * The header's badge: a brand gradient tile with the brand's own open-book glyph on it.
 *
 * [HEADER_BADGE_RADIUS] is a step below the tile's own size rather than half of it, so the tile reads
 * as a rounded square rather than as a circle wearing brand colours — which is what a `CircleShape`
 * here produced, and why the rest of the app's badges are all rounded rectangles too.
 */
private val HEADER_BADGE = 40.dp

private val HEADER_BADGE_RADIUS = 12.dp

private val HEADER_BADGE_GLYPH = 20.dp

/** Half the badge's width, so the heading column starts where the badge's optical left edge is. */
private val HEADER_GAP = 12.dp

private val HEADER_TITLE_SP = 17.sp

private val HEADER_SUBTITLE_SP = 11.sp

private val HEADER_SUBTITLE_GAP = 2.dp

/** Smaller than [AzkarTheme]'s 20dp default, because this glyph is a close mark, not a target. */
private val HEADER_CLOSE_GLYPH = 14.dp

/**
 * The breathing room between the heading and the bar that says where in the walk the user is.
 *
 * Tight, because the bar is the same fact as the subtitle above it in a different language: give them
 * a full block's gap and the header stops reading as one thing.
 */
private val HEADER_STEP_GAP = 12.dp

/**
 * The bar: a fat short rule per page, with a gap you can see the background through.
 *
 * Four dp rather than a hairline, because the segments are meant to read as filled *area* — progress —
 * and a one-pixel rule with a rounded cap reads as a divider. Rounded all the way because the eye
 * should read "a pill that is either full or empty", and square ends on a 4dp-tall bar are almost
 * indistinguishable from nothing at all.
 */
private val STEP_BAR_HEIGHT = 4.dp

private val STEP_BAR_RADIUS = 2.dp

/** Wide enough that the bar reads as separate pages rather than as one dashed line. */
private val STEP_BAR_GAP = 4.dp

/**
 * Five hundred characters, and the room left for the counter that says so.
 *
 * A limit rather than nothing because the description is the one field with no ceiling of its own, and
 * an unbounded one turns a collection card into a wall of text on every screen that shows it. Five
 * hundred is roughly eighty words: enough to say what a collection is *for*, which is all the detail
 * page shows of it.
 */
private const val DESCRIPTION_LIMIT: Int = 500

/**
 * Three lines of the app's 13sp body text, and the band underneath them that the counter sits in.
 *
 * The height is 32dp of Material's own field padding, three 20dp lines, and 12dp left over — so the
 * last line can never reach the counter however long the prose gets, and the counter never moves
 * however short it is. The line cap is what makes the arithmetic hold; without it the text would keep
 * growing downward through the band the moment anyone wrote a fourth line.
 */
private const val DESCRIPTION_LINES: Int = 3

private val DESCRIPTION_FIELD_HEIGHT = 104.dp

private val DESCRIPTION_COUNT_SP = 10.sp

/** The counter sits a field's own padding in from the outline, level with the text it counts. */
private val DESCRIPTION_COUNT_INSET = 10.dp

/**
 * Lower than [DESCRIPTION_COUNT_INSET] would suggest, because the end inset has to clear the field's own
 * end padding — Material's 16dp — or the counter would look like it was escaping the box.
 */
private val DESCRIPTION_COUNT_BOTTOM_INSET = 8.dp

/** Big enough to judge a picture by, small enough not to argue with the name field above it. */
private val COVER_PREVIEW_SIZE = 72.dp

/** Tighter than the panel's own 16dp, so the picture reads as an object inside it. */
private val COVER_PREVIEW_RADIUS = 8.dp

private val COVER_PREVIEW_GLYPH = 28.dp

private val COVER_GAP = 12.dp

/** Both lines of the cover's status, which is a heading and a sentence rather than two captions. */
private val COVER_STATUS_SP = 12.sp

/**
 * The gap between the language chips and the note above them, and between two chips.
 *
 * Wider than [FIELD_GAP] because the chips are a row of choices rather than a stack of fields, and a row
 * that hugs its label reads as a caption to the label rather than as a set of things to press.
 */
private val TRANSLATION_CHIP_GAP = 10.dp

/**
 * The gap above one language's block.
 *
 * Larger than any gap inside the dialog. A translation block is a separate thing being said about the
 * same collection, and at [FIELD_GAP] apart the fields would run together into one long form — which is
 * exactly the reading this is trying not to encourage.
 */
private val TRANSLATION_BLOCK_GAP = 24.dp

/** The note explaining that one name does not become two languages by itself. */
private val HINT_SP = 11.sp

/** Small enough to sit beside the block's title without competing with the field under it. */
private val TRANSLATION_REMOVE_GLYPH = 14.dp

private val COVER_HINT_SP = 11.sp
