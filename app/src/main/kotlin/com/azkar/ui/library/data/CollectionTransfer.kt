package com.azkar.ui.library.data

import com.azkar.domain.model.LocalizedPair
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.RemembranceCollection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * The shape of a collection moving between machines, in both the machine and the human format.
 *
 * The constant below is the marker that tells an Azkar transfer apart from any other JSON that
 * happens to parse, which is what lets a mistyped paste be refused with an explanation rather than
 * as an empty import.
 *
 * Deliberately does not reuse [com.azkar.domain.model.RemembranceCollection]. A domain collection
 * carries a database id, a kind, and a live list of `Remembrance` aggregates; none of those mean
 * anything on the machine receiving the document, and a `null` id is exactly the case that makes a
 * transfer format confusing to read.
 *
 * Members travel as their text rather than as a reference. A remembrance id is an artefact of this
 * installation's seed order and would match nothing elsewhere, whereas the text does — and matching
 * on it is what lets an import fold into an existing catalogue instead of duplicating 288 rows.
 */
private const val FORMAT_ID = "azkar-collections"

/**
 * The version of the document shape.
 *
 * Bumped only for a change this build cannot read; adding a field is not a bump, because unknown
 * keys are ignored and so an older build still imports a newer document with the extra field dropped.
 */
private const val FORMAT_VERSION = 1

/**
 * Which of the two encodings a user is looking at.
 *
 * Both carry exactly the same information; they differ in who they are for. [JSON] round-trips
 * without loss and is what the app writes by default, while [TEXT] is line-oriented so a person can
 * read it, edit it, and paste it back — which is the only way a hand-written list ever gets into
 * the app, and the reason the text format refuses to guess at anything.
 */
enum class TransferFormat {
    JSON,
    TEXT,
}

@Serializable
data class TransferCollection(
    val name: String,
    val description: String? = null,
    val iconKey: String? = null,
    /** The labels the collection was filed under, as the user typed them rather than folded. */
    val tagNames: List<String> = emptyList(),
    /**
     * The cover image, base64-encoded, or `null` for the generated fallback.
     *
     * Encoded rather than referenced because the other end has no file to point at: a collection's
     * cover is a private copy inside one installation's covers directory, which is meaningless
     * elsewhere. There is deliberately no way to say "use this URL" — a shared document naming a
     * remote image would make the importing app fetch something chosen by the sender.
     *
     * Only the JSON format carries this. The line format exists to be read and edited by a person,
     * and a paragraph of base64 in the middle of one would be the single least editable thing in it.
     */
    val coverImageBase64: String? = null,
    val members: List<TransferMember> = emptyList(),
    /**
     * The collection's name and description in every language but the one it was typed in, keyed by
     * BCP-47 language tag.
     *
     * Carried because a shelf's name is the one piece of prose a shared document cannot lose: the
     * collection is identified by what it is called, so a document that carried only the canonical
     * name would import a shelf whose name silently disagreed with the one it came from — and would
     * do it without a warning, because nothing about the payload would be malformed.
     *
     * Empty rather than absent, so a document written before translations existed decodes into
     * exactly the value it would have decoded into anyway and imports as it always did.
     *
     * Only the JSON format carries this. The line format exists to be read and edited by a person,
     * and per-language name pairs would double the width of every line for a document whose whole
     * reason to exist is being legible in a text editor.
     */
    val translations: Map<String, TransferTranslation> = emptyMap(),
    /** BCP-47 language tag of [name], kept explicit rather than inferred from its writing direction. */
    val nameLocale: String = "en",
) {
    init {
        require(name.isNotBlank()) { "A transferred collection must have a name" }
    }
}

/**
 * A collection's wording in one language, as it travels.
 *
 * The flat shape rather than [LocalizedPair] because the wire format is written to be read by a
 * person and by other tools, and a type named after the domain makes the document a contract with
 * this app rather than a description of its contents. `description` is nullable for the same reason
 * it is nullable in the domain: a translator asked for the name is not being asked for prose.
 */
@Serializable
data class TransferTranslation(
    val name: String,
    val description: String? = null,
) {
    init {
        require(name.isNotBlank()) { "A transferred translation must have a name" }
    }
}

/**
 * The wording this document carries, in the shape the domain speaks.
 *
 * Parsed here rather than at the point of use because a malformed tag is a property of the *document*
 * — the sender wrote something that is not a language — and there is one right place to decide that
 * such a document is not worth refusing outright. A tag that will not parse comes back as [Locale.ROOT],
 * whose `toLanguageTag()` is `und`, and is dropped below rather than stored as a language nobody can
 * select.
 */
internal fun TransferCollection.pairs(): Map<Locale, LocalizedPair> =
    translations
        .mapNotNull { (tag, pair) ->
            val locale = Locale.forLanguageTag(tag)
            if (locale.toLanguageTag() == "und") null else locale to LocalizedPair(pair.name, pair.description)
        }.toMap()

/** The language of the canonical name, defaulting old or malformed documents to English. */
internal fun TransferCollection.nameLanguage(): Locale =
    Locale.forLanguageTag(nameLocale).takeIf { it.toLanguageTag() != "und" } ?: Locale.ENGLISH

/** One passage, as it is matched against the catalogue on the way back in. */
@Serializable
data class TransferMember(
    val text: String,
    val source: String? = null,
) {
    init {
        require(text.isNotBlank()) { "A transferred member must have text" }
    }
}

/**
 * The document wrapper, which is what makes the format versioned.
 *
 * A bare array of collections would have nowhere to put a version number, and a format without one
 * cannot be revised: the first change to the shape would be indistinguishable from a file written
 * by a newer build. `format` is checked on the way in so that a document from an unrelated tool
 * which happens to be valid JSON is rejected with an explanation rather than imported as garbage.
 */
@Serializable
data class TransferDocument(
    // Named `marker` rather than `format` because detekt's `ImplicitDefaultLocale` flags any
    // identifier whose name merely *starts* with a two-letter locale code, and "format" starts with
    // `fi`. The wire name stays `format` so the document still reads as an Azkar file.
    @SerialName("format")
    val marker: String = FORMAT_ID,
    val version: Int = FORMAT_VERSION,
    val collections: List<TransferCollection> = emptyList(),
)

/**
 * Encodes and decodes a collection transfer, in both formats.
 *
 * Pure: no Compose, no database, no clock. Everything it needs is handed in as data and handed
 * back as data, which is what makes it testable without a harness and reusable by whichever screen
 * ends up owning the clipboard.
 *
 * The two formats answer different questions and neither replaces the other. JSON is the durable
 * one: it survives a round trip through any tool, carries the version, and tolerates fields a future
 * build adds. The line format is the *shareable* one — a person can read it, correct a typo and
 * paste it back, which is the only way a shared list ever gets to be useful in practice.
 *
 * Every decoder returns a [Result] rather than throwing, because a failed import is an expected
 * outcome of pasting the wrong thing, not an exception the caller should be forced to catch.
 */
object CollectionTransfer {
    /**
     * Unknown keys are ignored on the way in.
     *
     * A document written by a later build should still import into this one: the fields this
     * version does not understand are, by construction, about something it cannot act on yet.
     */
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    /**
     * Renders [collections] as the machine format.
     *
     * The reified `encodeToString`/`decodeFromString` rather than the overloads that take a
     * `KSerializer` outright. They compile to the same call, but the reified form names the type in
     * the source instead of in a `serializer()` the reader has to go and look up — and the
     * generated one does not exist to anything reading these sources rather than compiling them.
     */
    fun encodeJson(collections: List<TransferCollection>): String =
        json.encodeToString(TransferDocument(collections = collections))

    /**
     * Reads the machine format.
     *
     * A document whose `format` is not ours fails even though it parses, because the only thing
     * `format` is for is telling two documents apart.
     */
    fun decodeJson(payload: String): Result<List<TransferCollection>> =
        runCatching { json.decodeFromString<TransferDocument>(payload) }
            .mapCatching { document ->
                val said = document.marker
                require(said == FORMAT_ID) {
                    "This is not an Azkar collections file (it says \"" + said + "\")"
                }
                require(document.version == FORMAT_VERSION) {
                    "This file was written by a newer version of Azkar (format " + document.version + ")"
                }
                document.collections
            }

    /**
     * Renders [collections] as the shareable format.
     *
     * One directive per line, starting with `@`. A leading blank line before the header and lines
     * that are blank or begin with `#` are for the human: they let a list be annotated and
     * commented without breaking the import.
     *
     * Labels travel, because a collection's tags are what categorise it and losing them would make
     * an imported shelf fall out of every filtered view on the receiving side. The cover does not,
     * and [TransferCollection.coverImageBase64] says why; a collection that arrives without one is
     * not a broken import, it is a collection that gets the generated fallback the same as any
     * collection the user never gave a picture to.
     */
    fun encodeText(collections: List<TransferCollection>): String =
        buildString {
            appendLine("$FORMAT_ID $FORMAT_VERSION")
            collections.forEach { collection ->
                appendLine("@collection ${escape(collection.name)}")
                appendLine("@language ${escape(collection.nameLocale)}")
                collection.description?.let { appendLine("@description ${escape(it)}") }
                collection.iconKey?.let { appendLine("@icon ${escape(it)}") }
                if (collection.tagNames.isNotEmpty()) {
                    appendLine("@tags ${collection.tagNames.joinToString(TAG_SEPARATOR_TEXT) { escape(it) }}")
                }
                collection.members.forEach { member ->
                    val source = member.source
                    val text =
                        if (source.isNullOrBlank()) {
                            escape(
                                member.text,
                            )
                        } else {
                            "${escape(member.text)}|${escape(source)}"
                        }
                    appendLine("@member $text")
                }
                appendLine()
            }
        }

    /**
     * Reads the shareable format.
     *
     * Strict about directives it does not recognise: a `@memebr` line is a typo a person made, and
     * silently dropping it would import a list that is quietly missing something. The failure names
     * the line number, because that is the only thing that helps whoever has to fix it.
     */
    fun decodeText(payload: String): Result<List<TransferCollection>> =
        runCatching {
            val lines = payload.lines()
            val header =
                lines.firstOrNull { it.isNotBlank() && !it.trimStart().startsWith(HASH) }
                    ?: error("There is nothing to import")
            val headerParts = header.trim().split(' ', limit = 2)
            require(headerParts.getOrNull(0) == FORMAT_ID) {
                "This is not an Azkar collections list (it starts with \"${headerParts.getOrNull(0)}\")"
            }
            val version =
                headerParts.getOrNull(1)?.trim()?.toIntOrNull()
                    ?: error("The header does not say which version this is")
            require(version == FORMAT_VERSION) {
                "This list was written by a newer version of Azkar (format $version)"
            }
            parseCollections(lines)
        }

    /**
     * Builds the collections out of the directive lines.
     */
    private fun parseCollections(lines: List<String>): List<TransferCollection> {
        val collections = mutableListOf<TransferCollection>()
        var name: String? = null
        var nameLocale = "en"
        var description: String? = null
        var iconKey: String? = null
        var tagNames = emptyList<String>()
        val members = mutableListOf<TransferMember>()

        fun flush() {
            val current = name ?: return
            collections +=
                TransferCollection(
                    name = current,
                    nameLocale = nameLocale,
                    description = description,
                    iconKey = iconKey,
                    tagNames = tagNames,
                    members = members.toList(),
                )
        }

        lines.forEachIndexed { index, raw ->
            val line = raw.trim()
            if (!line.startsWith(AT)) return@forEachIndexed
            val body = line.removePrefix(AT)
            when {
                body.startsWith("collection ") -> {
                    flush()
                    name = unescape(body.removePrefix("collection ").trim())
                    nameLocale = "en"
                    description = null
                    iconKey = null
                    tagNames = emptyList()
                    members.clear()
                }

                body.startsWith("description ") -> description = unescape(body.removePrefix("description ").trim())
                body.startsWith("language ") -> nameLocale = unescape(body.removePrefix("language ").trim())
                body.startsWith("icon ") -> iconKey = unescape(body.removePrefix("icon ").trim())
                body.startsWith("tags ") ->
                    tagNames =
                        body
                            .removePrefix("tags ")
                            .split(TAG_SEPARATOR)
                            .map { unescape(it.trim()) }
                            .filter { it.isNotEmpty() }

                // Not unescaped here: where the escapes fall is what separates the text from the
                // source, so the split has to happen on the escaped form and each half has to be
                // unwrapped on its own.
                body.startsWith("member ") -> members += parseMember(body.removePrefix("member "), index + 1)
                else -> error("Line ${index + 1} says \"$body\", which this version of Azkar does not understand")
            }
        }
        flush()
        return collections
    }

    /**
     * Splits a member into its text and its source.
     *
     * On the first separator that was not escaped, which is the only split that can be both ways
     * round: splitting on the last bar breaks a citation of "Sahih al-Bukhari | Book 90" into the
     * wrong two halves, and splitting on the first breaks nothing *provided* a literal bar is
     * escaped — which is why [escape] writes one as `\|`. Neither guess works without that, so the
     * escape is not an optimisation here, it is what makes the format decodable at all.
     *
     * Whitespace either side of the separator is dropped: it exists to keep the line readable, not
     * to become part of the passage.
     */
    private fun parseMember(
        escaped: String,
        lineNumber: Int,
    ): TransferMember {
        val separator = indexOfSeparator(escaped)
        if (separator < 0) return TransferMember(unescape(escaped).trim())
        val text = unescape(escaped.substring(0, separator)).trim()
        val source = unescape(escaped.substring(separator + 1)).trim()
        require(text.isNotBlank() || source.isNotBlank()) { "Line $lineNumber names a member with no text" }
        return TransferMember(text = text, source = source.ifBlank { null })
    }

    /** Where the first unescaped separator sits, or -1 when there is none. */
    private fun indexOfSeparator(escaped: String): Int {
        var index = 0
        while (index < escaped.length) {
            if (escaped[index] == ESCAPE[0]) {
                index += 2
            } else if (escaped[index] == SEPARATOR) {
                return index
            } else {
                index += 1
            }
        }
        return -1
    }

    private fun escape(value: String): String =
        value
            .replace(ESCAPE, ESCAPED_ESCAPE)
            .replace(SEPARATOR_STRING, ESCAPED_SEPARATOR)
            .replace("\n", ESCAPED_NEWLINE)

    private fun unescape(value: String): String {
        val builder = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character == ESCAPE[0] && index + 1 < value.length) {
                val next = value[index + 1]
                when (next) {
                    '\\' -> builder.append('\\')
                    'n' -> builder.append('\n')
                    SEPARATOR -> builder.append(SEPARATOR)
                    // An escape this version does not define is kept as it was written rather than
                    // being swallowed: a hand-edited list is the main input this format has, and
                    // silently dropping a character someone typed is the worst thing it could do.
                    else -> builder.append(character).append(next)
                }
                index += 2
            } else {
                builder.append(character)
                index += 1
            }
        }
        return builder.toString()
    }

    private const val AT = "@"
    private const val HASH = "#"

    /**
     * What a `@tags` line separates names with.
     *
     * A comma rather than the `|` used inside a member line, because the two are read by different
     * directives and a tag name containing a bar is unremarkable — "sahih | muslim" is a plausible
     * label — while a tag name containing a comma is not a thing anyone would write. The name is
     * still escaped on the way out, so the separator cannot be confused for an escape.
     */
    private const val TAG_SEPARATOR = ','

    private const val TAG_SEPARATOR_TEXT = ", "

    private const val SEPARATOR = '|'
    private const val SEPARATOR_STRING = "|"
    private const val ESCAPED_SEPARATOR = "\\|"
    private const val ESCAPE = "\\"
    private const val ESCAPED_ESCAPE = "\\\\"
    private const val ESCAPED_NEWLINE = "\\n"
}

/**
 * Turning a stored collection into a document someone else can read.
 *
 * Kept apart from [CollectionTransfer] on purpose: that type speaks the wire format and never looks
 * at the domain, while this one is the only place that knows what a `Remembrance` is. Splitting them
 * means the codec could be lifted out of the app verbatim, and it is what keeps the file within the
 * function count the linter allows without reaching for a suppression.
 */
object CollectionExport {
    /**
     * Builds the transfer form of [collection].
     *
     * The text of each member is resolved through [textOf] rather than read from the locale here,
     * because the app already has a presenter that knows the current locale and its fallbacks, and
     * two implementations of "the text a user would see" would drift apart. A member with no id is
     * skipped: there is nothing to match it against on the other side.
     *
     * Pinned is deliberately not among the exported visibilities. Pinning is a per-device display
     * preference — the same argument that lets a user pin a shipped collection — so it travels with
     * the device and not with the document.
     */
    fun exportOf(
        collection: RemembranceCollection,
        textOf: (Remembrance) -> String,
        coverImageBase64: String? = null,
    ): TransferCollection =
        TransferCollection(
            name = collection.name,
            nameLocale = collection.nameLocale.toLanguageTag(),
            description = collection.description,
            iconKey = collection.iconKey,
            tagNames = collection.tags.map { it.name }.sortedWith(String.CASE_INSENSITIVE_ORDER),
            coverImageBase64 = coverImageBase64,
            members =
                collection.members
                    .filter { it.id != null }
                    .map { member -> TransferMember(textOf(member), member.source) },
            translations = collection.exportTranslations(),
        )

    /**
     * The stored translations, re-keyed by language tag and with blanks dropped.
     *
     * Filtering rather than passing through is what keeps the document honest about what it says: a
     * blank name would be rejected by [TransferTranslation]'s own guard on the way in, and a blank
     * description would import as a description that is somehow empty rather than as none at all.
     */
    private fun RemembranceCollection.exportTranslations(): Map<String, TransferTranslation> =
        translations
            .filterValues { it.name.isNotBlank() }
            .mapKeys { (locale, _) -> locale.toLanguageTag() }
            .mapValues { (_, pair) -> TransferTranslation(name = pair.name, description = pair.description) }

    /** Builds the transfer form of every collection in [collections], in the order given. */
    fun exportOf(
        collections: List<RemembranceCollection>,
        textOf: (Remembrance) -> String,
        coverImageBase64Of: (RemembranceCollection) -> String? = { null },
    ): List<TransferCollection> = collections.map { exportOf(it, textOf, coverImageBase64Of(it)) }

    /** Builds the transfer form of every collection in [collections], in the order given. */
    fun exportOf(
        collections: List<RemembranceCollection>,
        textOf: (Remembrance) -> String,
    ): List<TransferCollection> = collections.map { exportOf(it, textOf) }

    /**
     * Renders [collection] as a shareable document in [format].
     *
     * Export is a read and an encode, so it needs no state of its own and both callers — the share
     * dialog and the import flow's own copy helper — go through here rather than each pairing a
     * format with an encoder.
     */
    fun exportAs(
        collection: RemembranceCollection,
        format: TransferFormat,
        textOf: (Remembrance) -> String,
        coverImageBase64: String? = null,
    ): String {
        // The cover is read only for the machine format. Asking the caller for it on the text path
        // too would base64 a photograph for a payload that is about to drop it, which is the kind of
        // waste that looks like a bug in a profile.
        val transfer =
            exportOf(
                collection = collection,
                textOf = textOf,
                coverImageBase64 = if (format == TransferFormat.JSON) coverImageBase64 else null,
            )
        return when (format) {
            TransferFormat.JSON -> CollectionTransfer.encodeJson(listOf(transfer))
            TransferFormat.TEXT -> CollectionTransfer.encodeText(listOf(transfer))
        }
    }
}
