package com.azkar.data.seed

import com.azkar.domain.model.LocalizedPair

/**
 * The collections the application ships, and the tag whose remembrances each one is seeded with.
 *
 * A shipped collection is an ordinary row once it is in the database. [CollectionSeed] writes its
 * membership out as [com.azkar.data.table.CollectionMemberTable] rows at the moment it runs, so from
 * that point the shelf is reading one table for every collection and there is no second place where
 * a collection's contents could be spelled. The tag is kept here because it is what the seed resolves
 * membership through on the way in — not because anything reads it afterwards.
 *
 * The consequence worth stating plainly: adding azkar in a later release no longer flows into these
 * collections by itself, because there is nothing left that would notice. Shipping new azkar means
 * writing the migration that adds the new rows, which is a deliberate thing to do and visible in a
 * diff rather than a silent no-op on the user's existing install.
 *
 * Nothing here is editable by the user at runtime and nothing here runs twice. [CollectionSeed] is a
 * one-shot bootstrap, not a migration step; see [LibraryBootstrapper].
 *
 * The wording in other languages lives here rather than in the compose resources because these are
 * rows in a database that a reader has to be able to see in their own language, not text a
 * composition resolves: a shelf card holding `أذكار الصباح` is reading a stored row, not drawing a
 * localised string.
 */
internal object OfficialCollectionCatalogue {
    /** BCP-47 tags, spelled here rather than derived so this file and the tag vocabulary cannot disagree. */
    private const val ENGLISH = "en"
    private const val ARABIC = "ar"

    data class Entry(
        /**
         * The tag this collection is seeded from, or `null` when it is not seeded from a tag.
         *
         * Also the collection's own label, so a shipped shelf arrives categorised rather than
         * anonymous.
         */
        val tagName: String?,
        val name: String,
        val description: String,
        val iconKey: String,
        /**
         * The same shelf under other names, keyed by BCP-47 language tag.
         *
         * `name` above is the *canonical* spelling: it is what the row stores, what a rename
         * collides with, and what the collection is still called when no translation matches. That
         * makes it a key rather than a fallback nobody reaches for — the last thing to do would be to
         * ship the Arabic as the fallback and quietly change what a user's own English-typed
         * collection is called.
         *
         * The description is carried here beside its name rather than looked up separately because a
         * shelf's card draws both together. A translation with a name and no description is allowed
         * (see [com.azkar.domain.model.LocalizedPair]) but it is a half-translated card, and the
         * reader is in a better position to judge that than a rule would be.
         *
         * Both languages are written out rather than generated, because this file is the reviewable
         * one. A reader who wants to check what a shelf is called in Arabic should be able to open a
         * single file and find it, rather than follow a key into another.
         */
        val translations: Map<String, LocalizedPair>,
    )

    /**
     * Ordered as the shelf presents them, most fundamental first: time of day, then occasion, then
     * situation, then subject. A new entry is appended rather than inserted so that the ordering is a
     * product decision rather than an accident of how the list happened to be typed.
     */
    val entries: List<Entry> =
        listOf(
            entry(
                tagName = "Morning",
                name = "Morning Azkar",
                description = "Remembrance for the start of the day",
                iconKey = "sunrise",
                arabic = LocalizedPair(name = "أذكار الصباح", description = "أذكار لبداية اليوم"),
            ),
            entry(
                tagName = "Evening",
                name = "Evening Azkar",
                description = "Remembrance for the end of the day",
                iconKey = "sunset",
                arabic = LocalizedPair(name = "أذكار المساء", description = "أذكار لنهاية اليوم"),
            ),
            entry(
                tagName = "Morning & Evening",
                name = "Morning & Evening",
                description = "Remembrance recited at both ends of the day",
                iconKey = "sun",
                arabic = LocalizedPair(name = "أذكار الصباح والمساء", description = "أذكار تُقال في أول اليوم وآخره"),
            ),
            entry(
                tagName = "Prayer",
                name = "After Prayer",
                description = "Remembrance said once the prayer is finished",
                iconKey = "mosque",
                arabic = LocalizedPair(name = "أذكار بعد الصلاة", description = "أذكار تُقال بعد انتهاء الصلاة"),
            ),
            entry(
                tagName = "Travel",
                name = "Travel",
                description = "Remembrance for setting out and arriving",
                iconKey = "compass",
                arabic = LocalizedPair(name = "أذكار السفر", description = "أذكار عند الخروج والوصول"),
            ),
            entry(
                tagName = "Home & Family",
                name = "Home & Family",
                description = "Remembrance for the home and those in it",
                iconKey = "home",
                arabic = LocalizedPair(name = "البيت والأسرة", description = "أذكار البيت وأهله"),
            ),
            entry(
                tagName = "Food & Drink",
                name = "Food & Drink",
                description = "Remembrance before and after eating",
                iconKey = "khatim",
                arabic = LocalizedPair(name = "الطعام والشراب", description = "أذكار قبل الطعام وبعده"),
            ),
            entry(
                tagName = "Nature",
                name = "Nature",
                description = "Remembrance for rain, wind, sun and sky",
                iconKey = "nature",
                arabic = LocalizedPair(name = "الطبيعة", description = "أذكار المطر والرياح والشمس والسماء"),
            ),
            entry(
                tagName = "Good Etiquette",
                name = "Good Etiquette",
                description = "Remembrance for how to treat people",
                iconKey = "people",
                arabic = LocalizedPair(name = "حسن الأدب", description = "أذكار في معاملة الناس"),
            ),
            entry(
                tagName = "Praising Allah",
                name = "Praising Allah",
                description = "Remembrance that glorifies the Creator",
                iconKey = "star",
                arabic = LocalizedPair(name = "الثناء على الله", description = "أذكار في الثناء على الخالق"),
            ),
            entry(
                tagName = "Joy & Distress",
                name = "Joy & Distress",
                description = "Remembrance for times of ease and hardship",
                iconKey = "heart",
                arabic = LocalizedPair(name = "الفرح والهمّ", description = "أذكار في وقت الفرج والهم"),
            ),
            entry(
                tagName = "Sickness & Death",
                name = "Sickness & Death",
                description = "Remembrance for illness, recovery and loss",
                iconKey = "heart",
                arabic = LocalizedPair(name = "المرض والموت", description = "أذكار المرض والشفاء والفقد"),
            ),
            entry(
                tagName = "Hajj & Umrah",
                name = "Hajj & Umrah",
                description = "Remembrance for the pilgrimage",
                iconKey = "khatim",
                arabic = LocalizedPair(name = "الحجّ والعمرة", description = "أذكار الحج والعمرة"),
            ),
        )

    /**
     * Builds an entry whose canonical spelling is English, with English registered as a translation too.
     *
     * The English translation row is written on purpose rather than left implicit. Two reasons, and
     * the second is the one that decides it: the row is what makes a collection added by an *import*
     * and a collection this catalogue seeded resolve identically — a reader asking "which rows carry
     * English?" should not get two different answers depending on where a shelf came from — and it is
     * what lets a later release translate the canonical name into a language the seed originally
     * shipped in without that being a schema change.
     *
     * The Arabic wording arrives as one [LocalizedPair] rather than as two loose strings, because
     * that is the shape a translation actually has — a shelf's card draws a name and its description
     * together, and passing them separately is what made this function long enough to be a mistake.
     */
    private fun entry(
        tagName: String?,
        name: String,
        description: String,
        iconKey: String,
        arabic: LocalizedPair,
    ): Entry =
        Entry(
            tagName = tagName,
            name = name,
            description = description,
            iconKey = iconKey,
            translations =
                mapOf(
                    ENGLISH to LocalizedPair(name = name, description = description),
                    ARABIC to arabic,
                ),
        )
}
