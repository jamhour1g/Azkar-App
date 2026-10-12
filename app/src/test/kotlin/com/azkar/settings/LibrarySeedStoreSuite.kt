package com.azkar.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

/**
 * None of the shipping path used to write down that the dataset had been seeded, so a second
 * [CollectionSeed] run would restart the dataset. This suite pins the one place that now holds
 * those two answers — whether this installation was seeded, and which row is the starred shelf —
 * to a real preferences file, so nothing reaches the dev's actual settings.
 */
val LibrarySeedStoreSuite by testSuite("The seed bookkeeping") {
    test("a fresh install has not been seeded yet") {
        withSeedFile { file, scope ->
            val store = LibrarySeedStore(SettingsStore.create(file, scope))

            assertThat(store.isSeeded()).isFalse()
        }
    }

    test("marking seeded is what the next launch reads back") {
        withSeedFile { file, scope ->
            val store = LibrarySeedStore(SettingsStore.create(file, scope))
            store.markSeeded()

            val copy = file.resolveSibling("copy.preferences_pb")
            Files.copy(file, copy)
            val nextRun = LibrarySeedStore(SettingsStore.create(copy, scope))

            assertThat(nextRun.isSeeded()).isTrue()
        }
    }

    test("a fresh install has no favourites shelf id") {
        withSeedFile { file, scope ->
            val store = LibrarySeedStore(SettingsStore.create(file, scope))

            assertThat(store.favoritesCollectionId()).isNull()
        }
    }

    test("the favourites shelf id survives a restart") {
        withSeedFile { file, scope ->
            val store = LibrarySeedStore(SettingsStore.create(file, scope))
            store.setFavoritesCollectionId(42L)

            val copy = file.resolveSibling("copy.preferences_pb")
            Files.copy(file, copy)
            val nextRun = LibrarySeedStore(SettingsStore.create(copy, scope))

            assertThat(nextRun.favoritesCollectionId()).isEqualTo(42L)
        }
    }

    test("forgetting drops the gate and the shelf id together") {
        withSeedFile { file, scope ->
            val store = LibrarySeedStore(SettingsStore.create(file, scope))
            store.markSeeded()
            store.setFavoritesCollectionId(42L)

            store.forget()

            val copy = file.resolveSibling("copy.preferences_pb")
            Files.copy(file, copy)
            val nextRun = LibrarySeedStore(SettingsStore.create(copy, scope))
            assertThat(nextRun.isSeeded()).isFalse()
            assertThat(nextRun.favoritesCollectionId()).isNull()
        }
    }

    test("the stored key is the name the file format already has") {
        assertThat(LibrarySeedStore.seededKey).isEqualTo(booleanPreferencesKey("seed_completed"))
        assertThat(LibrarySeedStore.favoritesCollectionIdKey)
            .isEqualTo(longPreferencesKey("favorites_collection_id"))
    }
}

private inline fun withSeedFile(block: (Path, CoroutineScope) -> Unit) {
    val directory = Files.createTempDirectory("azkar-seed")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
        block(directory.resolve("settings.preferences_pb"), scope)
    } finally {
        scope.cancel()
        runCatching { Files.list(directory).forEach { it.deleteIfExists() } }
        runCatching { directory.deleteIfExists() }
    }
}
