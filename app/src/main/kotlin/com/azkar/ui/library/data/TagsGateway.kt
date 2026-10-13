package com.azkar.ui.library.data

import com.azkar.domain.exception.TagNameTakenException
import com.azkar.domain.model.TagSummary
import com.azkar.domain.model.TagUsage
import com.azkar.domain.service.TagService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.Locale

private val logger = KotlinLogging.logger {}

/**
 * The seam between the tags screen and everything that stores a tag.
 *
 * Its own interface rather than another one added to [CollectionsGateway], because a tag is not a
 * collection and the shelf has no business being able to delete one: [CollectionsGateway] is the seam
 * the shelf, the detail page and the session all share, and a capability none of them need is a
 * capability any of them could start using by accident. The tags screen is the only caller here, so
 * the interface can be exactly the seven questions it asks.
 */
interface TagGateway {
    /** Every tag in use, with how many collections and remembrances carry each. */
    suspend fun fetchSummaries(): List<TagSummary>

    /**
     * Every tag that has been deleted, most recently first.
     *
     * A second read rather than a flag on [fetchSummaries] because "what has been removed" and "what
     * exists" are different questions with different answers, and the screen shows them one at a time —
     * a list carrying both would have to be split again at the call site to draw a heading over each.
     */
    suspend fun fetchDeleted(): List<TagSummary>

    /**
     * Adds a tag called [name], reporting what happened rather than only throwing.
     *
     * Shares its result type with [rename] because it is the same question asked about a different
     * row — "is that name free, and what did you do about it" — and the screen's answers are the same
     * four sentences either way. Two types would have been two exhaustive `when`s in the screen that
     * happen to be written out identically, which is how the two would drift apart the first time a
     * fifth answer was needed for one of them.
     *
     * [translations] are the other-language names for the same tag, keyed by the language each is
     * written in. Optional: a tag with none reads the same everywhere, which is a legitimate tag.
     */
    suspend fun create(
        name: String,
        translations: Map<Locale, String> = emptyMap(),
        nameLocale: Locale = Locale.ENGLISH,
    ): TagSaveResult

    /**
     * Renames the tag identified by [id], reporting what happened rather than only throwing.
     *
     * A rename can fail in a way the user has to be told about — the name is taken — and a failure
     * that arrives as an exception has to be recognised by its type to become a sentence. Doing that
     * translation here means the screen asks a question and gets an answer, and the one place that
     * knows the domain's exception types is the layer that is allowed to import them.
     *
     * The name check covers [translations] as well as the name itself, so a tag cannot be renamed out
     * from under another tag by claiming a word another tag already answers to.
     */
    suspend fun rename(
        id: Long,
        newName: String,
        translations: Map<Locale, String>? = null,
        nameLocale: Locale? = null,
    ): TagSaveResult

    /**
     * Deletes the tag identified by [id], keeping it and its attachments so the delete can be undone.
     *
     * Named for what it does rather than for the storage, because the two have come apart: this used
     * to remove the row, and a screen that says "deleted" while a name is still recoverable would be
     * promising more permanence than the operation has.
     */
    suspend fun softDelete(id: Long)

    /** Brings a deleted tag back, with every collection and remembrance it was attached to. */
    suspend fun restore(id: Long)

    /**
     * The shelves and remembrances carrying the tag [id], or `null` when it names no live tag.
     *
     * The drill-down behind a row's usage panel, and the only method here that reads outside the tag
     * tables. [fetchSummaries] answers how many things carry a label; this answers which, which cannot
     * be had from a count and has to follow the id out into both sides of the join.
     *
     * `null` rather than an empty answer, so the panel can tell a tag that is gone from a tag that is
     * on nothing — two situations that look identical in a list of zero rows.
     */
    suspend fun usage(id: Long): TagUsage?
}

/**
 * What naming a tag did, as the screen needs to hear it.
 *
 * A sealed type rather than a nullable tag because "it worked and here it is" and "it did not work"
 * are the only two answers, and the second has three different reasons a user would want spelled out
 * differently. Collapsing them to `null` would throw away exactly the distinction the screen exists
 * to draw.
 *
 * [Missing] is reachable only from [TagGateway.rename] and not from [TagGateway.create] — a create
 * cannot target a row that is not there. It stays on the shared type because a case that cannot occur
 * on one path costs one line, whereas two types means the screen branches on the shape of the request
 * before it can branch on the answer.
 */
sealed interface TagSaveResult {
    /** The name was free and the tag now carries it. */
    data object Saved : TagSaveResult

    /** Another live tag already holds that name, so nothing was written. */
    data object NameTaken : TagSaveResult

    /** There is no tag under that id — the row was deleted between the read and the write. */
    data object Missing : TagSaveResult

    /** The name was blank, so there was nothing to write. Caught before the database was asked. */
    data object Blank : TagSaveResult
}

/**
 * Production [TagGateway], over [TagService].
 *
 * No dependencies bag, unlike [DomainCollectionsGateway]: the list is one long because there is one
 * service behind it. The rule the bag exists for is "adding a concern must not mean editing every
 * composition root", and a bag with a single field is a constructor wearing a different name — it
 * should be introduced when the second dependency arrives, not before.
 */
class DomainTagsGateway(
    private val tags: TagService,
) : TagGateway {
    override suspend fun fetchSummaries(): List<TagSummary> = tags.findSummaries()

    override suspend fun fetchDeleted(): List<TagSummary> = tags.findDeletedSummaries()

    /**
     * Translates the domain failures a caller has a sentence for, and lets the rest through.
     *
     * `runCatching` would catch everything, so the translation is a catch per failure rather than a
     * blanket rethrow: [TagNameTakenException] means the user asked for something impossible and the
     * screen has words for it, a blank name means they typed nothing and the screen should say so
     * rather than look like a failure, while a persistence failure means the write did not happen for
     * a reason no sentence would be honest about. Those are different answers, and the screen has one
     * generic failure notice for the last of them.
     *
     * The blank case is [IllegalArgumentException] because that is what `TagImpl`'s `require` throws.
     * Reading the domain's guard as a value here rather than adding a second check in the screen is
     * what keeps the screen and the domain from disagreeing about what a tag is allowed to be called.
     */
    override suspend fun create(
        name: String,
        translations: Map<Locale, String>,
        nameLocale: Locale,
    ): TagSaveResult =
        try {
            tags.create(name, translations, nameLocale)
            TagSaveResult.Saved
        } catch (taken: TagNameTakenException) {
            logger.debug { "Tag '${taken.name}' could not be added: the name is already in use" }
            TagSaveResult.NameTaken
        } catch (blank: IllegalArgumentException) {
            logger.debug { "Adding a tag was rejected as blank: ${blank.message}" }
            TagSaveResult.Blank
        }

    override suspend fun rename(
        id: Long,
        newName: String,
        translations: Map<Locale, String>?,
        nameLocale: Locale?,
    ): TagSaveResult =
        try {
            tags.rename(id, newName, translations, nameLocale)
            TagSaveResult.Saved
        } catch (taken: TagNameTakenException) {
            logger.debug { "Rename of tag $id rejected: the name '${taken.name}' is already in use" }
            TagSaveResult.NameTaken
        } catch (blank: IllegalArgumentException) {
            logger.debug { "Rename of tag $id rejected as blank: ${blank.message}" }
            TagSaveResult.Blank
        } catch (missing: IllegalStateException) {
            logger.debug { "Rename of tag $id rejected: ${missing.message}" }
            TagSaveResult.Missing
        }

    override suspend fun softDelete(id: Long) = tags.deleteById(id)

    override suspend fun restore(id: Long) = tags.restore(id)

    /**
     * A read, so nothing is caught here: a usage panel that silently opened empty because a join
     * failed would hide the failure behind a panel that looks deliberate.
     */
    override suspend fun usage(id: Long): TagUsage? = tags.findUsage(id)
}
