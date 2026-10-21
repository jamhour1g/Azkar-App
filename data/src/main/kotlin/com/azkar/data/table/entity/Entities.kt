package com.azkar.data.table.entity

import com.azkar.data.table.CollectionMemberTable
import com.azkar.data.table.CollectionTable
import com.azkar.data.table.CollectionTagTable
import com.azkar.data.table.CollectionTranslationTable
import com.azkar.data.table.ExplanationTranslationTable
import com.azkar.data.table.RecitationProgressTable
import com.azkar.data.table.RemembranceTable
import com.azkar.data.table.RemembranceTagTable
import com.azkar.data.table.RemembranceTranslationTable
import com.azkar.data.table.TagTable
import com.azkar.data.table.TagTranslationTable
import com.azkar.domain.model.CollectionKind
import com.azkar.domain.model.HadithGrade
import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.CompositeEntity
import org.jetbrains.exposed.v1.dao.CompositeEntityClass
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass
import java.time.OffsetDateTime

// One entity class per table in `com.azkar.data.table`, and nothing else.
//
// The columns are delegated to the table rather than restated: `var name: String by TagTable.name`
// reads from the row and writes back to it, so a field cannot be declared here that the schema does
// not have, and a column the schema gains is invisible to this file until something asks for it.
// That is the whole argument for the DAO layer existing — the alternative is a handwritten
// `ResultRow` -> domain mapper beside every repository, which is a second place for a column to be
// spelled out and no way at all for the compiler to notice the two have drifted.
//
// A key column is typed `EntityID<Long>` rather than `Long`. That is the one cost, and it is paid
// where it is unavoidable rather than everywhere: the entity classes below are the only place it
// appears, and the repositories below them mostly hold raw ids because `findById` and `forIds`
// take the raw value.
//
// Timestamps are `OffsetDateTime` because the columns are `TIMESTAMP WITH TIME ZONE`. The domain
// model speaks `Instant`; the conversion belongs in the mapping extensions, not here.

/** One label in the shared vocabulary both remembrances and collections draw on. */
class TagEntity(
    id: EntityID<Long>,
) : LongEntity(id) {
    companion object : LongEntityClass<TagEntity>(TagTable)

    var name: String by TagTable.name

    var nameLocaleCode: String by TagTable.nameLocaleCode

    var createdAt: OffsetDateTime by TagTable.createdAt

    var updatedAt: OffsetDateTime by TagTable.updatedAt

    /** `null` while the tag is in use. See `TagTable.deletedAt`. */
    var deletedAt: OffsetDateTime? by TagTable.deletedAt
}

/** The core zikr record. Its text and commentary live in the two translation tables below. */
class RemembranceEntity(
    id: EntityID<Long>,
) : LongEntity(id) {
    companion object : LongEntityClass<RemembranceEntity>(RemembranceTable)

    var sourceText: String? by RemembranceTable.sourceText

    var grade: HadithGrade by RemembranceTable.grade

    var isFavorite: Boolean by RemembranceTable.isFavorite

    var createdAt: OffsetDateTime by RemembranceTable.createdAt

    var updatedAt: OffsetDateTime by RemembranceTable.updatedAt
}

/**
 * What [RemembranceTranslationEntity], [ExplanationTranslationEntity] and [TagTranslationEntity]
 * have in common.
 *
 * Not a superclass, and deliberately so: it cannot be one. `EntityClass<E>` needs `E` to be an
 * `Entity<E>`, and the classes below are `Entity<RemembranceTranslationEntity>`,
 * `Entity<ExplanationTranslationEntity>` and `Entity<TagTranslationEntity>` respectively — a shared
 * abstract `LongEntity` base would have to declare itself the entity type, which it is not.
 *
 * An interface is the one shape that works, and it is enough: the loader and the writer need to
 * read a text's locale, body and parent and to reconcile those without caring which of the tables
 * a row is in.
 */
internal interface LocalizedText {
    /**
     * This row's own key, as a plain id.
     *
     * Spelled out rather than inherited: `Entity<ID>` already declares `id`, and a second
     * declaration of the same name in an interface the class also implements would be an accidental
     * override of a member the class gets from its superclass. Renaming it costs one line per entity
     * and keeps the mapper generic over every table.
     */
    val rowId: Long

    /**
     * The row this text belongs to.
     *
     * Named for the role rather than for the table it points at, because the same declaration now
     * covers three tables pointing at three different parents — and a writer handed this interface
     * has to be able to ask "which row of mine is this" without knowing which table it was reading.
     * Each entity answers it with its own table's column, which is what `by` does here.
     */
    var ownerId: Long

    var localeCode: String

    var text: String

    var createdAt: OffsetDateTime

    var updatedAt: OffsetDateTime
}

/** A remembrance's own words in one locale. */
class RemembranceTranslationEntity(
    id: EntityID<Long>,
) : LongEntity(id),
    LocalizedText {
    companion object : LongEntityClass<RemembranceTranslationEntity>(RemembranceTranslationTable)

    override val rowId: Long get() = id.value

    override var ownerId: Long by RemembranceTranslationTable.ownerId

    override var localeCode: String by RemembranceTranslationTable.localeCode

    override var text: String by RemembranceTranslationTable.text

    override var createdAt: OffsetDateTime by RemembranceTranslationTable.createdAt

    override var updatedAt: OffsetDateTime by RemembranceTranslationTable.updatedAt
}

/** The commentary on a remembrance, in one locale. Structurally identical to the above. */
class ExplanationTranslationEntity(
    id: EntityID<Long>,
) : LongEntity(id),
    LocalizedText {
    companion object : LongEntityClass<ExplanationTranslationEntity>(ExplanationTranslationTable)

    override val rowId: Long get() = id.value

    override var ownerId: Long by ExplanationTranslationTable.ownerId

    override var localeCode: String by ExplanationTranslationTable.localeCode

    override var text: String by ExplanationTranslationTable.text

    override var createdAt: OffsetDateTime by ExplanationTranslationTable.createdAt

    override var updatedAt: OffsetDateTime by ExplanationTranslationTable.updatedAt
}

/**
 * A label's wording in one locale. Structurally identical to the above, against a different parent.
 *
 * Separate rather than a generic entity over all three tables, because an entity class is bound to
 * one table's columns and one `EntityClass`, and a union of three would need every read to cast
 * from `LocalizedText` back to whichever table produced it.
 */
class TagTranslationEntity(
    id: EntityID<Long>,
) : LongEntity(id),
    LocalizedText {
    companion object : LongEntityClass<TagTranslationEntity>(TagTranslationTable)

    override val rowId: Long get() = id.value

    override var ownerId: Long by TagTranslationTable.ownerId

    override var localeCode: String by TagTranslationTable.localeCode

    override var text: String by TagTranslationTable.text

    override var createdAt: OffsetDateTime by TagTranslationTable.createdAt

    override var updatedAt: OffsetDateTime by TagTranslationTable.updatedAt
}

/** One row of the remembrances-to-tags link. The pair *is* the identity. */
class RemembranceTagEntity(
    id: EntityID<CompositeID>,
) : CompositeEntity(id) {
    companion object : CompositeEntityClass<RemembranceTagEntity>(RemembranceTagTable)

    var remembranceId: EntityID<Long> by RemembranceTagTable.remembranceId

    var tagId: EntityID<Long> by RemembranceTagTable.tagId

    // The table carries the audit columns even though nothing reads them back: a link row is written
    // once and deleted whole, so a stamp on it would answer no question. Declared anyway, because the
    // schema has them and an entity that silently omits a column is the drift this layer exists to
    // prevent.
    var createdAt: OffsetDateTime by RemembranceTagTable.createdAt

    var updatedAt: OffsetDateTime by RemembranceTagTable.updatedAt
}

/** A shelf of remembrances. Its members are [CollectionMemberEntity] rows, not a column. */
class CollectionEntity(
    id: EntityID<Long>,
) : LongEntity(id) {
    companion object : LongEntityClass<CollectionEntity>(CollectionTable)

    var name: String by CollectionTable.name

    var nameLocaleCode: String by CollectionTable.nameLocaleCode

    var description: String? by CollectionTable.description

    var kind: CollectionKind by CollectionTable.kind

    var iconKey: String? by CollectionTable.iconKey

    var coverImagePath: String? by CollectionTable.coverImagePath

    var pinnedAt: OffsetDateTime? by CollectionTable.pinnedAt

    var lastOpenedAt: OffsetDateTime? by CollectionTable.lastOpenedAt

    var homePinnedAt: OffsetDateTime? by CollectionTable.homePinnedAt

    var isFavorite: Boolean by CollectionTable.isFavorite

    var position: Int? by CollectionTable.position

    var createdAt: OffsetDateTime by CollectionTable.createdAt

    var updatedAt: OffsetDateTime by CollectionTable.updatedAt
}

/**
 * A shelf's name and description in one locale.
 *
 * Not a [LocalizedText] even though it is the same idea against a different parent: that interface
 * promises a single `text`, and this row carries two pieces of prose. A shelf card draws both at
 * once, so a name with no description beside it is a row that reads half-translated — which is an
 * ordinary state here, and one the reader handles rather than the writer.
 */
class CollectionTranslationEntity(
    id: EntityID<Long>,
) : LongEntity(id) {
    companion object : LongEntityClass<CollectionTranslationEntity>(CollectionTranslationTable)

    var collectionId: Long by CollectionTranslationTable.collectionId

    var localeCode: String by CollectionTranslationTable.localeCode

    var name: String by CollectionTranslationTable.name

    var description: String? by CollectionTranslationTable.description

    var createdAt: OffsetDateTime by CollectionTranslationTable.createdAt

    var updatedAt: OffsetDateTime by CollectionTranslationTable.updatedAt
}

/** One member of one collection, at one position in its display order. */
class CollectionMemberEntity(
    id: EntityID<CompositeID>,
) : CompositeEntity(id) {
    companion object : CompositeEntityClass<CollectionMemberEntity>(CollectionMemberTable)

    var collectionId: EntityID<Long> by CollectionMemberTable.collectionId

    var remembranceId: EntityID<Long> by CollectionMemberTable.remembranceId

    var position: Int by CollectionMemberTable.position

    var createdAt: OffsetDateTime by CollectionMemberTable.createdAt

    var updatedAt: OffsetDateTime by CollectionMemberTable.updatedAt
}

/** One label attached to one collection. */
class CollectionTagEntity(
    id: EntityID<CompositeID>,
) : CompositeEntity(id) {
    companion object : CompositeEntityClass<CollectionTagEntity>(CollectionTagTable)

    var collectionId: EntityID<Long> by CollectionTagTable.collectionId

    var tagId: EntityID<Long> by CollectionTagTable.tagId

    var createdAt: OffsetDateTime by CollectionTagTable.createdAt

    var updatedAt: OffsetDateTime by CollectionTagTable.updatedAt
}

/** How many times one member of one collection has been counted. */
class RecitationProgressEntity(
    id: EntityID<CompositeID>,
) : CompositeEntity(id) {
    companion object : CompositeEntityClass<RecitationProgressEntity>(RecitationProgressTable)

    var collectionId: EntityID<Long> by RecitationProgressTable.collectionId

    var remembranceId: EntityID<Long> by RecitationProgressTable.remembranceId

    var repeatCount: Int by RecitationProgressTable.repeatCount

    var lastRecitedAt: OffsetDateTime? by RecitationProgressTable.lastRecitedAt

    var createdAt: OffsetDateTime by RecitationProgressTable.createdAt

    var updatedAt: OffsetDateTime by RecitationProgressTable.updatedAt
}

/**
 * The identity of one [CollectionMemberEntity] row.
 *
 * Building a composite key by hand is the part of the DAO layer with no shorthand, and it comes up
 * on every create and every `findById` for the four pair-keyed tables. These four functions are the
 * whole of it.
 *
 * The return type is `CompositeID` and not `EntityID<CompositeID>` because that is what
 * `CompositeEntityClass` is declared over — it extends `EntityClass<CompositeID, E>`, so `new(id)`
 * and `findById(id)` take the bare key. The entity's own `id` property is the `EntityID` wrapper
 * around one of these, and that is where the owning table gets recorded.
 *
 * `CompositeID { }` is the companion's `invoke`. Its lambda takes the key as a *parameter* rather
 * than as a receiver, and assigning the raw value to a key column is what registers the mapping —
 * assigning an `EntityID` instead asks for a different overload and does not compile.
 */
internal fun collectionMemberId(
    collectionId: Long,
    remembranceId: Long,
): CompositeID =
    CompositeID { key ->
        key[CollectionMemberTable.collectionId] = collectionId
        key[CollectionMemberTable.remembranceId] = remembranceId
    }

/** The identity of one [CollectionTagEntity] row. */
internal fun collectionTagId(
    collectionId: Long,
    tagId: Long,
): CompositeID =
    CompositeID { key ->
        key[CollectionTagTable.collectionId] = collectionId
        key[CollectionTagTable.tagId] = tagId
    }

/** The identity of one [RecitationProgressEntity] row. */
internal fun recitationProgressId(
    collectionId: Long,
    remembranceId: Long,
): CompositeID =
    CompositeID { key ->
        key[RecitationProgressTable.collectionId] = collectionId
        key[RecitationProgressTable.remembranceId] = remembranceId
    }

/** The identity of one [RemembranceTagEntity] row. */
internal fun remembranceTagId(
    remembranceId: Long,
    tagId: Long,
): CompositeID =
    CompositeID { key ->
        key[RemembranceTagTable.remembranceId] = remembranceId
        key[RemembranceTagTable.tagId] = tagId
    }
