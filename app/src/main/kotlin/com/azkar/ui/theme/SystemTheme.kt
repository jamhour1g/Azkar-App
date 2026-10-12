package com.azkar.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Toolkit
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * The colour scheme the desktop is itself using.
 *
 * [UNKNOWN] is a real answer and not a failure to try harder: it is what the app gets when the
 * platform will not say, and the caller decides what to do about it.
 */
enum class SystemTheme {
    LIGHT,
    DARK,
    UNKNOWN,
    ;

    /** Whether this is a dark desktop. Anything we could not read counts as light. */
    val isDark: Boolean get() = this == DARK
}

/**
 * Asks the desktop which palette it is using.
 *
 * This exists because `isSystemInDarkTheme()` cannot be trusted here. On desktop it bottoms out in
 * Skiko's `currentSystemTheme`, which on Linux loads `libdbus-1.so` at runtime and reads the
 * `color-scheme` property of `org.freedesktop.appearance`. Two things have to line up for that: the
 * unversioned library name has to be on disk, which only a `-dev` install provides, and that schema
 * has to be installed, which GNOME does not ship — it publishes the same property under
 * `org.gnome.desktop.interface`. When either is missing Skiko answers [SystemTheme.UNKNOWN] and
 * Compose reports "light" for a desktop that is plainly dark, silently and forever.
 *
 * So the platform is asked directly instead, per platform:
 *  - Windows publishes the answer as an AWT desktop property (`win.appUsableDarkBackground`);
 *  - macOS does the same (`apple.awt.application.appearance`);
 *  - everywhere else is the freedesktop desktop, where the answer lives in GNOME's own settings and,
 *    failing that, in the theme names Plasma and GTK keep in their config files.
 *
 * Called once per launch rather than per frame: this can spawn a process, and the OS changing theme
 * while the window is open is answered by picking a palette explicitly, which is what the header
 * toggle does and what gets stored.
 */
fun readSystemTheme(): SystemTheme {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        os.startsWith("windows") -> windowsSystemTheme()
        os.contains("mac") || os.contains("darwin") -> macSystemTheme()
        else -> freedesktopSystemTheme()
    }
}

/** The desktop's colour scheme, resolved once for the life of the composition. */
@Composable
fun rememberSystemTheme(): SystemTheme = remember { readSystemTheme() }

/**
 * The Windows look and feel publishes the app's own background colour, and whether that background is
 * dark is the answer. The key only exists from Windows 10 on; an older desktop says nothing.
 */
private fun windowsSystemTheme(): SystemTheme =
    when (val background = desktopProperty(WINDOWS_DARK_BACKGROUND)) {
        is Boolean -> if (background) SystemTheme.DARK else SystemTheme.LIGHT
        else -> SystemTheme.UNKNOWN
    }

/**
 * On macOS the value is the appearance name, "Dark" or "Light", and it is only set for a real
 * application rather than a bare JVM — hence the null check.
 */
private fun macSystemTheme(): SystemTheme =
    desktopProperty(MACOS_APPEARANCE)
        ?.toString()
        ?.themeNameOrNull()
        ?: SystemTheme.UNKNOWN

/**
 * GNOME first, because it is the one freedesktop desktop that states the answer outright; Plasma
 * and plain GTK only ever publish theme names.
 */
private fun freedesktopSystemTheme(): SystemTheme =
    gsettingsColorScheme()
        ?: kdeColorScheme()
        ?: gtkThemeColorScheme()
        ?: SystemTheme.UNKNOWN

/**
 * GNOME's own answer, read with `gsettings` because the value lives in dconf and dconf has no
 * file to read.
 *
 * A missing `gsettings` — off GNOME, or in a container — is the ordinary case rather than an error,
 * so the failure is swallowed and the next source gets its turn.
 */
private fun gsettingsColorScheme(): SystemTheme? =
    commandOutput(
        "gsettings",
        "get",
        "org.gnome.desktop.interface",
        "color-scheme",
    ).gsettingsColorScheme()

/**
 * Plasma's look and feel or colour scheme, from its own config file.
 *
 * "BreezeDark" and "Adwaita-dark" both say dark in the name, which is the only signal KDE gives.
 */
private fun kdeColorScheme(): SystemTheme? =
    iniValue(
        readTextOrNull(configFile(KDE_GLOBALS)),
        listOf(KDE_LOOK_AND_FEEL_KEY, KDE_COLOR_SCHEME_KEY),
    )?.themeNameOrNull()

/** GTK keeps the theme name in the versioned settings file, so both are worth a look. */
private fun gtkThemeColorScheme(): SystemTheme? =
    GTK_SETTINGS_FILES
        .firstNotNullOfOrNull { settings ->
            iniValue(readTextOrNull(configFile(settings)), listOf(GTK_THEME_KEY))?.themeNameOrNull()
        }

/**
 * An AWT desktop property, which is how both Windows and macOS are asked.
 *
 * The property is published by the platform look and feel, so a desktop using something else — or a
 * headless JVM behind the screenshot harness — has nothing to answer with, which is a null rather
 * than a light palette.
 */
private fun desktopProperty(key: String): Any? =
    runCatching { Toolkit.getDefaultToolkit().getDesktopProperty(key) }.getOrNull()

/**
 * Runs [command] and returns its single line of output, or null when it cannot be run.
 *
 * Bounded because this runs before the window opens: a `gsettings` that hung would hang the launch.
 * The read happens after the wait, which is safe because the output is one short line — a process
 * that had to be killed for overrunning the budget has nothing worth reading anyway.
 */
private fun commandOutput(vararg command: String): String? =
    runCatching {
        val process = ProcessBuilder(*command).start()
        if (!process.waitFor(COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            null
        } else {
            process.inputStream
                .bufferedReader()
                .use { it.readLine() }
                ?.trim()
        }
    }.getOrNull()

/**
 * The value of the first of [keys] present in the INI text [text], or null.
 *
 * A dozen lines instead of a parser dependency for two keys. A key matches with or without spaces
 * around its `=`, because that is how these files are actually written — Plasma in particular — and
 * the section headers that `[KDE]` and `[General]` introduce are simply lines that name no key.
 */
internal fun iniValue(
    text: String?,
    keys: List<String>,
): String? =
    text
        ?.lineSequence()
        ?.map { line -> line.substringBefore('#').trim() to line.substringAfter('=', "") }
        ?.firstOrNull { (line, _) -> keys.any { key -> line.substringBefore('=').trim() == key } }
        ?.second
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/**
 * Reads a GNOME `color-scheme` value, which is quoted and admits a third answer.
 *
 * `'default'` means no preference has been set, which is not the same as light — the desktop may well
 * be following something else — so it falls through to the next source instead of answering.
 */
internal fun String?.gsettingsColorScheme(): SystemTheme? {
    val value = this?.trim()?.trim('\'', '"')?.lowercase() ?: return null
    return when {
        value.contains("dark") -> SystemTheme.DARK
        value.contains("light") -> SystemTheme.LIGHT
        else -> null
    }
}

/**
 * Reads a theme name, where "dark" in the name is the whole signal.
 *
 * Unlike [gsettingsColorScheme] there is no third answer: a name that does not say dark is the light
 * theme, and the only names these files hold are theme names.
 */
internal fun String?.themeNameOrNull(): SystemTheme? =
    this
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { if (it.contains("dark", ignoreCase = true)) SystemTheme.DARK else SystemTheme.LIGHT }

/** A file under the user's config directory, honouring `XDG_CONFIG_HOME` the way a freedesktop app must. */
private fun configFile(relative: String): Path =
    Path
        .of(
            System.getenv("XDG_CONFIG_HOME").orEmpty().takeIf { it.isNotBlank() }
                ?: Path.of(System.getProperty("user.home"), ".config").toString(),
            relative,
        )

private fun readTextOrNull(file: Path): String? = runCatching { Files.readString(file) }.getOrNull()

private const val WINDOWS_DARK_BACKGROUND = "win.appUsableDarkBackground"

private const val MACOS_APPEARANCE = "apple.awt.application.appearance"

private const val KDE_GLOBALS = "kdeglobals"

private const val KDE_LOOK_AND_FEEL_KEY = "LookAndFeelPackage"

private const val KDE_COLOR_SCHEME_KEY = "ColorScheme"

private const val GTK_THEME_KEY = "gtk-theme-name"

private val GTK_SETTINGS_FILES = listOf("gtk-4.0/settings.ini", "gtk-3.0/settings.ini")

private const val COMMAND_TIMEOUT_MILLIS = 1_000L
