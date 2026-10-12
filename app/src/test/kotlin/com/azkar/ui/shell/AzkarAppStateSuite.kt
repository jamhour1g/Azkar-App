package com.azkar.ui.shell

import com.azkar.i18n.ARABIC
import com.azkar.i18n.ENGLISH
import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat
import java.util.Locale

/**
 * Switching the application language.
 *
 * The invariant worth protecting is that the two copies of the active language cannot drift:
 * `org.jetbrains.compose.resources` reads the JVM default, while the tree recomposes off
 * [AzkarAppState.locale]. Nothing derives one from the other, so a change that updates only one of
 * them would leave the window titled in one language and drawn in another, with no error anywhere.
 *
 * [AzkarAppState] takes its persistence as a constructor argument precisely so these can assert on
 * the write without touching the real `settings.preferences_pb`.
 */
val AzkarAppStateSuite by testSuite("Switching the application language") {
    test("a switch updates the shell state and the JVM default together") {
        withRestoredLocale {
            val persisted = mutableListOf<Locale>()
            val state = AzkarAppState(initialLocale = ENGLISH, persistTheme = {}) { persisted += it }

            state.applyLocale(ARABIC)

            assertThat(state.locale).isEqualTo(ARABIC)
            // The resources system reads the JVM default, not the state holder, so the two agreeing
            // is the whole mechanism.
            assertThat(Locale.getDefault()).isEqualTo(ARABIC)
            assertThat(persisted).containsExactly(ARABIC)
        }
    }

    test("re-picking the current language changes nothing and writes nothing") {
        withRestoredLocale {
            val before = Locale.getDefault()
            val persisted = mutableListOf<Locale>()
            val state = AzkarAppState(initialLocale = ARABIC, persistTheme = {}) { persisted += it }

            // A different tag for the same language must still count as no change: a user who picks
            // "ar" again must not trigger the rebuild that re-reads every resource. The settings
            // screen lists every language at once, so there is no OK to press that could filter it.
            state.applyLocale(Locale.forLanguageTag("ar-EG"))

            assertThat(state.locale).isEqualTo(ARABIC)
            // Untouched, not forced to ARABIC: the state was seeded directly here, so only a real
            // switch is allowed to move the process-wide default.
            assertThat(Locale.getDefault()).isEqualTo(before)
            assertThat(persisted).isEmpty()
        }
    }
}

/** Runs [block] with whatever locale it leaves behind, then puts the original one back. */
private fun withRestoredLocale(block: () -> Unit) {
    val original = Locale.getDefault()
    try {
        block()
    } finally {
        Locale.setDefault(original)
    }
}

/**
 * Switching the colour theme.
 *
 * The invariant is that a choice outlives the window. `selectTheme` used to move
 * [AzkarAppState.themePreference] and stop there, so the palette flipped for the rest of the session
 * and the next launch went back to following the system — the app looked like it had forgotten the
 * toggle. Reading it back is [ThemePreferenceStore.read]'s job; writing it is this suite's.
 *
 * Both persistence callbacks are constructor arguments for the same reason the language suite can
 * assert on its write: the real ones touch `settings.preferences_pb`.
 */
val AzkarAppThemeSuite by testSuite("Switching the application theme") {
    test("choosing a theme moves the palette and stores the choice") {
        val persisted = mutableListOf<ThemePreference>()
        val state = AzkarAppState(persistTheme = { persisted += it }, persist = {})

        state.selectTheme(ThemePreference.DARK)

        assertThat(state.themePreference).isEqualTo(ThemePreference.DARK)
        assertThat(persisted).containsExactly(ThemePreference.DARK)
    }

    test("a stored preference is what the window opens with") {
        val persisted = mutableListOf<ThemePreference>()
        val state =
            AzkarAppState(
                initialThemePreference = ThemePreference.DARK,
                persistTheme = { persisted += it },
                persist = {},
            )

        assertThat(state.themePreference).isEqualTo(ThemePreference.DARK)
        // Opening is a read, not a change. Storing the launch value back would mean a dark
        // preference was silently rewritten to SYSTEM on every start that failed to read the file.
        assertThat(persisted).isEmpty()
    }

    test("switching back stores the switch back") {
        val persisted = mutableListOf<ThemePreference>()
        val state =
            AzkarAppState(
                initialThemePreference = ThemePreference.DARK,
                persistTheme = { persisted += it },
                persist = {},
            )

        state.selectTheme(ThemePreference.LIGHT)

        assertThat(state.themePreference).isEqualTo(ThemePreference.LIGHT)
        assertThat(persisted).containsExactly(ThemePreference.LIGHT)
    }

    test("the system preference is reachable again") {
        val persisted = mutableListOf<ThemePreference>()
        val state =
            AzkarAppState(
                initialThemePreference = ThemePreference.DARK,
                persistTheme = { persisted += it },
                persist = {},
            )

        state.selectTheme(ThemePreference.SYSTEM)

        // It was the launch default and then nothing else: the header's toggle offered two states,
        // so a user who pinned dark had no way back. The settings screen lists all three.
        assertThat(state.themePreference).isEqualTo(ThemePreference.SYSTEM)
        assertThat(persisted).containsExactly(ThemePreference.SYSTEM)
    }

    test("re-picking the theme already in force writes again but never drifts") {
        val persisted = mutableListOf<ThemePreference>()
        val state =
            AzkarAppState(
                initialThemePreference = ThemePreference.LIGHT,
                persistTheme = { persisted += it },
                persist = {},
            )

        state.selectTheme(ThemePreference.LIGHT)

        // Unlike the language, this writes: the option is stated rather than toggled, so the row the
        // user just clicked is the only way to name the current value. Re-storing it is harmless.
        assertThat(state.themePreference).isEqualTo(ThemePreference.LIGHT)
        assertThat(persisted).containsExactly(ThemePreference.LIGHT)
    }
}
