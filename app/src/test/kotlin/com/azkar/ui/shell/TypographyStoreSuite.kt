package com.azkar.ui.shell

import com.azkar.settings.SettingsStore
import com.azkar.settings.copyForNextLaunch
import com.azkar.settings.withSettingsFile
import com.azkar.ui.theme.AppTypography
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat

/**
 * That a reader's face and size are still there on the next launch, and that a value this build
 * cannot honour is turned into one it can rather than thrown at the theme.
 *
 * The face half is the one with a real failure mode and it is not a crash: a settings file that
 * travels to a machine without the chosen face installed would otherwise leave the theme asking for
 * a family that does not resolve. [TypographyStore.read] answering with a usable value is what keeps
 * that a cosmetic problem, and the only way to test it is to write a name this machine does not have
 * — which is what `putStoredFamily` exists for.
 *
 * Round trips go through a copy of the file rather than a second store over the same path, because
 * DataStore allows one instance per file per process and the second one throws rather than reading.
 */
val TypographyStoreSuite by testSuite("The reader's typography") {
    test("a fresh install reads as the bundled pairing at full size") {
        withSettingsFile { file, scope ->
            val store = TypographyStore(SettingsStore.create(file, scope))

            assertThat(runBlocking { store.read() }).isEqualTo(AppTypography())
            assertThat(AppTypography().isAzkarFont).isTrue()
            assertThat(AppTypography().scale).isEqualTo(1f)
        }
    }

    test("a chosen face and size are what the next launch reads") {
        withSettingsFile { file, scope ->
            val chosen = AppTypography(family = "Georgia", scalePercent = 130)
            TypographyStore(SettingsStore.create(file, scope)).persistAndAwait(chosen)

            val nextLaunch = TypographyStore(SettingsStore.create(copyForNextLaunch(file), scope))

            assertThat(runBlocking { nextLaunch.read() }).isEqualTo(chosen)
        }
    }

    test("a face this machine does not have still reads as a usable choice") {
        withSettingsFile { file, scope ->
            val store = TypographyStore(SettingsStore.create(file, scope))
            runBlocking {
                store.persistAndAwait(AppTypography(scalePercent = 110))
                store.putStoredFamily("A Face Only The Author Had")
            }

            val read = runBlocking { store.read() }

            // The size, which this build does understand, survives; the face is handed back as the
            // name that was stored, because deciding what to show for it is the settings screen's job
            // and it already does — the row turns into a "not installed" notice.
            assertThat(read.scalePercent).isEqualTo(110)
            assertThat(read.family).isEqualTo("A Face Only The Author Had")
        }
    }

    test("a stored size outside the slider's range is clamped rather than honoured") {
        withSettingsFile { file, scope ->
            val store = TypographyStore(SettingsStore.create(file, scope))
            runBlocking { store.persistAndAwait(AppTypography(scalePercent = 4_000)) }

            assertThat(runBlocking { store.read() }.scalePercent).isEqualTo(AppTypography.MAX_SCALE_PERCENT)
        }
    }

    test("a stored size below the slider's range is clamped the other way") {
        withSettingsFile { file, scope ->
            val store = TypographyStore(SettingsStore.create(file, scope))
            runBlocking { store.persistAndAwait(AppTypography(scalePercent = -50)) }

            assertThat(runBlocking { store.read() }.scalePercent).isEqualTo(AppTypography.MIN_SCALE_PERCENT)
        }
    }

    test("writing the bundled pairing back clears the face rather than storing a name for it") {
        withSettingsFile { file, scope ->
            val store = TypographyStore(SettingsStore.create(file, scope))
            runBlocking { store.persistAndAwait(AppTypography(family = "Georgia", scalePercent = 130)) }
            runBlocking { store.persistAndAwait(AppTypography()) }

            assertThat(runBlocking { store.read() }).isEqualTo(AppTypography())
        }
    }
}
