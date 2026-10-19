package com.azkar.ui.notifications

import com.azkar.settings.SettingsStore
import com.azkar.settings.copyForNextLaunch
import com.azkar.settings.withSettingsFile
import com.azkar.ui.home.PrayerKey
import com.azkar.ui.notifications.presentation.NotificationPresentationSettings
import com.azkar.ui.notifications.presentation.OverlayPlacement
import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat

/**
 * That every value the manager screen writes is a value the next launch reads.
 *
 * The screens do not write files; they call `update…` on this repository, and the repository
 * serialises. Nothing between the switch in the UI and the bytes on disk is covered by a screen test
 * or a model test, and the gap had a bug in it: the presentation settings carried a `private
 * companion object` for their bounds, and the generated serializer sits outside the class, so the
 * *first* time anybody moved a switch the write failed at runtime with an `IllegalAccessError` —
 * after a clean compile and a green suite. These tests are that gap, closed: each one writes through
 * the real repository against a real preferences file, then opens a second store over a copy of it,
 * which is what the next run of the app would do.
 *
 * `update…` is awaited rather than fired off, which is the whole reason there is no waiting here:
 * DataStore commits the file before the call returns, so the copy below cannot race it. (The prayer
 * switches in [com.azkar.ui.home.NotificationSettingsState] are fire-and-forget, which is why that
 * suite does not assert through a file at all and runs against the in-memory store instead.)
 */
val NotificationPreferencesRepositorySuite by testSuite("The notification preferences") {
    test("a fresh install has the overlay it is designed with") {
        withSettingsFile { file, scope ->
            val preferences = NotificationPreferencesRepository(SettingsStore.create(file, scope))
            preferences.refresh()

            val presentation = preferences.presentation.value
            assertThat(presentation.durationSeconds).isEqualTo(8)
            assertThat(presentation.placement).isEqualTo(OverlayPlacement.TOP_END)
            assertThat(presentation.maxVisible).isEqualTo(3)
        }
    }

    test("a presentation change is what the next launch reads") {
        withSettingsFile { file, scope ->
            val preferences = NotificationPreferencesRepository(SettingsStore.create(file, scope))
            preferences.refresh()
            val chosen =
                NotificationPresentationSettings(
                    durationSeconds = 20,
                    placement = OverlayPlacement.BOTTOM_START,
                    maxVisible = 4,
                )
            preferences.updatePresentation { chosen }

            val nextLaunch = NotificationPreferencesRepository(SettingsStore.create(copyForNextLaunch(file), scope))
            nextLaunch.refresh()

            assertThat(nextLaunch.presentation.value).isEqualTo(chosen)
            assertThat(preferences.presentation.value).isEqualTo(chosen)
        }
    }

    test("a presentation change is kept beside the queue rather than replacing it") {
        withSettingsFile { file, scope ->
            val preferences = NotificationPreferencesRepository(SettingsStore.create(file, scope))
            preferences.refresh()
            preferences.updateQueue { it.copy(enabled = true, mode = QueueMode.RANDOM, intervalMinutes = 45) }
            preferences.updatePresentation { it.copy(durationSeconds = 15) }

            val nextLaunch = NotificationPreferencesRepository(SettingsStore.create(copyForNextLaunch(file), scope))
            nextLaunch.refresh()

            assertThat(nextLaunch.queue.value.enabled).isTrue()
            assertThat(nextLaunch.queue.value.mode).isEqualTo(QueueMode.RANDOM)
            assertThat(nextLaunch.queue.value.intervalMinutes).isEqualTo(45)
            assertThat(nextLaunch.presentation.value.durationSeconds).isEqualTo(15)
        }
    }

    test("a queue change is what the next launch reads") {
        withSettingsFile { file, scope ->
            val preferences = NotificationPreferencesRepository(SettingsStore.create(file, scope))
            preferences.refresh()
            preferences.updateQueue { it.copy(collectionIds = listOf(4L, 9L), intervalMinutes = 30) }

            val nextLaunch = NotificationPreferencesRepository(SettingsStore.create(copyForNextLaunch(file), scope))
            nextLaunch.refresh()

            assertThat(nextLaunch.queue.value.collectionIds).containsExactly(4L, 9L)
            assertThat(nextLaunch.queue.value.intervalMinutes).isEqualTo(30)
        }
    }

    test("a prayer location is what the next launch calculates from") {
        withSettingsFile { file, scope ->
            val preferences = NotificationPreferencesRepository(SettingsStore.create(file, scope))
            preferences.refresh()
            val chosen =
                PrayerLocationSettings(
                    latitude = 41.01,
                    longitude = 28.98,
                    city = "Istanbul, Türkiye",
                    zoneId = "Europe/Istanbul",
                    method = PrayerCalculationMethod.KARACHI,
                    autoDetect = false,
                )
            preferences.savePrayerLocation(chosen)

            val nextLaunch = NotificationPreferencesRepository(SettingsStore.create(copyForNextLaunch(file), scope))
            nextLaunch.refresh()

            assertThat(nextLaunch.prayerLocation.value).isEqualTo(chosen)
        }
    }

    test("the five prayer switches are still the keys the home screen writes") {
        withSettingsFile { file, scope ->
            val preferences = NotificationPreferencesRepository(SettingsStore.create(file, scope))
            preferences.refresh()
            preferences.setPrayerEnabled(PrayerKey.ASR, false)
            preferences.setPrayerEnabled(PrayerKey.ISHA, false)

            val nextLaunch = NotificationPreferencesRepository(SettingsStore.create(copyForNextLaunch(file), scope))
            nextLaunch.refresh()

            assertThat(nextLaunch.prayerFlags.value[PrayerKey.ASR]).isFalse()
            assertThat(nextLaunch.prayerFlags.value[PrayerKey.ISHA]).isFalse()
            assertThat(nextLaunch.prayerFlags.value[PrayerKey.FAJR]).isTrue()
        }
    }
}
