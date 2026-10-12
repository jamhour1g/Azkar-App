package com.azkar.data.seed

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
 */
internal object OfficialCollectionCatalogue {
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
    )

    /**
     * Ordered as the shelf presents them, most fundamental first: time of day, then occasion, then
     * situation, then subject. A new entry is appended rather than inserted so that the ordering is a
     * product decision rather than an accident of how the list happened to be typed.
     */
    val entries: List<Entry> =
        listOf(
            Entry("Morning", "Morning Azkar", "Remembrance for the start of the day", "sunrise"),
            Entry("Evening", "Evening Azkar", "Remembrance for the end of the day", "sunset"),
            Entry("Morning & Evening", "Morning & Evening", "Remembrance recited at both ends of the day", "sun"),
            Entry("Prayer", "After Prayer", "Remembrance said once the prayer is finished", "mosque"),
            Entry("Travel", "Travel", "Remembrance for setting out and arriving", "compass"),
            Entry("Home & Family", "Home & Family", "Remembrance for the home and those in it", "home"),
            Entry("Food & Drink", "Food & Drink", "Remembrance before and after eating", "khatim"),
            Entry("Nature", "Nature", "Remembrance for rain, wind, sun and sky", "nature"),
            Entry("Good Etiquette", "Good Etiquette", "Remembrance for how to treat people", "people"),
            Entry("Praising Allah", "Praising Allah", "Remembrance that glorifies the Creator", "star"),
            Entry("Joy & Distress", "Joy & Distress", "Remembrance for times of ease and hardship", "heart"),
            Entry("Sickness & Death", "Sickness & Death", "Remembrance for illness, recovery and loss", "heart"),
            Entry("Hajj & Umrah", "Hajj & Umrah", "Remembrance for the pilgrimage", "khatim"),
        )
}
