package com.azkar.ui.library

import com.azkar.domain.model.CollectionSummary
import com.azkar.domain.model.TagSummary
import com.azkar.domain.model.TagUsage
import com.azkar.domain.model.TagUsageRemembrance
import com.azkar.ui.library.data.TagGateway
import com.azkar.ui.library.data.TagSaveResult
import java.util.Locale

/**
 * A tag store that answers from the lists it was handed.
 *
 * Shared with the screenshot suite. The two fields worth reading back in state tests — what the
 * last write was called, and whether the shelf was told —
 * are exactly the two things the screen is judged on. The mutable `tags` and `deleted` are not
 * laziness: the paging tests need the list to shrink *underneath* a reader who is already on a page,
 * which is the case that would otherwise need a second gateway.
 */
internal class FakeTagGateway(
    var tags: List<TagSummary>,
    var deleted: List<TagSummary>,
) : TagGateway {
    var lastCreated: String? = null
    var lastRenamedTo: String? = null
    var softDeleted: Long? = null
    var restored: Long? = null
    var refusedNames: Set<String> = emptySet()
    private val usage = mutableMapOf<Long, TagUsage>()

    override suspend fun fetchSummaries(): List<TagSummary> = tags

    override suspend fun fetchDeleted(): List<TagSummary> = deleted

    override suspend fun create(
        name: String,
        translations: Map<Locale, String>,
        nameLocale: Locale,
    ): TagSaveResult {
        lastCreated = name
        return if (name.lowercase() in refusedNames) TagSaveResult.NameTaken else TagSaveResult.Saved
    }

    override suspend fun rename(
        id: Long,
        newName: String,
        translations: Map<Locale, String>?,
        nameLocale: Locale?,
    ): TagSaveResult {
        lastRenamedTo = newName
        return if (newName.lowercase() in refusedNames) TagSaveResult.NameTaken else TagSaveResult.Saved
    }

    override suspend fun softDelete(id: Long) {
        softDeleted = id
    }

    override suspend fun restore(id: Long) {
        restored = id
    }

    override suspend fun usage(id: Long): TagUsage? = usage[id]

    /** What [usage] should answer with for [id], for a test that wants a panel with rows in it. */
    fun answeringUsage(
        id: Long,
        tag: TagSummary,
        collections: List<CollectionSummary> = emptyList(),
        remembrances: List<TagUsageRemembrance> = emptyList(),
    ) {
        usage[id] = TagUsage(tag = tag, collections = collections, remembrances = remembrances)
    }
}
