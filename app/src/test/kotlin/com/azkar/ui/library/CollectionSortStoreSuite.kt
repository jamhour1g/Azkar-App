package com.azkar.ui.library

import androidx.datastore.preferences.core.stringPreferencesKey
import com.azkar.settings.SettingsStore
import com.azkar.settings.copyForNextLaunch
import com.azkar.settings.withSettingsFile
import com.azkar.ui.library.model.CollectionSort
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CoroutineScope
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Path

/**
 * The stored order of the library, against a real DataStore on a real file.
 *
 * The shelf's sort is a choice the user makes once and expects to keep, the same way the colour
 * preference is: a shelf that reopened on a different order every launch would make the "My order"
 * sort — the one the queue recites in — a thing that has to be re-chosen every morning. The round
 * trip is asserted through a copy of the file rather than the same store, because DataStore allows
 * one instance per file per process and what a second run of the app reads is what actually landed
 * on disk.
 *
 * Each test gets its own file and its own store, so nothing reaches the developer's real settings.
 */
val CollectionSortStoreSuite by testSuite("The stored order of the library") {
    test("a fresh install opens on the fullest shelves first") {
        withSettingsFile { file, scope ->
            val store = collectionSorts(file, scope)

            assertThat(store.read()).isEqualTo(CollectionSort.MOST_ITEMS)
        }
    }

    test("a stored order is what the next run opens with") {
        withSettingsFile { file, scope ->
            val store = collectionSorts(file, scope)
            store.persistAndAwait(CollectionSort.NAME)

            // No waiting here: `edit` returns once DataStore has committed the file, so the copy
            // cannot race the write. [persistAndAwait] exists precisely so this suite never has to
            // guess.
            val nextLaunch = collectionSorts(copyForNextLaunch(file), scope)

            assertThat(nextLaunch.read()).isEqualTo(CollectionSort.NAME)
        }
    }

    test("a name this build does not know falls back to the fullest first") {
        withSettingsFile { file, scope ->
            val store = collectionSorts(file, scope)
            // Seeded under the production key rather than through [CollectionSortStore.persist],
            // because no sort this build has can produce a name it does not know.
            store.putStoredName("BY_VIBES")

            // A known order beats no order: a hand-edited file or a renamed constant must not take
            // the launch down with it.
            assertThat(store.read()).isEqualTo(CollectionSort.MOST_ITEMS)
        }
    }

    test("the stored key is the name the file format already has") {
        // The pinning is the point of this one, and it is why the suite does not simply assert on
        // [CollectionSortStore.key]: renaming the constant would silently drop everyone's saved
        // order, so the name itself has to be written out here and compared.
        assertThat(CollectionSortStore.key)
            .isEqualTo(stringPreferencesKey("library_collection_sort"))
    }
}

/** The sort store over a store built for [file] and [scope]. */
private fun collectionSorts(
    file: Path,
    scope: CoroutineScope,
): CollectionSortStore = CollectionSortStore(SettingsStore.create(file, scope))
