package com.azkar.ui.theme

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import com.azkar.i18n.ENGLISH
import com.azkar.ui.theme.AzkarTheme
import java.util.Locale

/**
 * The wrapper every `@Preview` in this app is expected to sit inside.
 *
 * [AzkarTheme] is usable as-is, but its two interesting parameters both have defaults that resolve
 * against the machine the preview happens to run on: `darkTheme` reads the developer's own desktop
 * theme through AWT and `gsettings`, and `uiLocale` reads `Locale.getDefault()`. That is the right
 * default for the running app and the wrong one for a preview, where it means half the previews in a
 * file render light and half render dark depending on who ran them last, and an Arabic layout only
 * shows up if the IDE happens to be in Arabic. Naming both at every call site costs a few tokens and
 * makes each preview say what it is actually showing.
 *
 * Direction follows from [uiLocale] rather than from a `CompositionLocal`: [AzkarTheme] computes
 * `LocalLayoutDirection` from `uiLocale.isArabic` on every pass, so overriding
 * `LocalLayoutDirection` underneath it would be silently undone. Passing an Arabic locale is the only
 * way to see the RTL layout this app actually ships.
 *
 * [width] and [height] exist because the shell's responsive branches key off
 * [LocalWindowSizeClass], which otherwise falls back to [DEFAULT_WINDOW_WIDTH] by
 * [DEFAULT_WINDOW_HEIGHT]. That default is the size the app opens at, so it is the right default
 * here too — but a preview that never names a size can only ever show the expanded layout, and the
 * compact rail and the medium shelf go unseen until someone resizes the real window.
 *
 * Nothing here reads or writes real state. Content is drawn from the fixtures in
 * `com.azkar.ui.preview`, which are in-memory and never wired into the Koin graph.
 */
@Composable
fun AzkarThemePreview(
    darkTheme: Boolean = false,
    uiLocale: Locale = ENGLISH,
    width: Dp = DEFAULT_WINDOW_WIDTH,
    height: Dp = DEFAULT_WINDOW_HEIGHT,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalWindowSizeClass provides windowSizeClassFor(width, height),
    ) {
        AzkarTheme(
            darkTheme = darkTheme,
            uiLocale = uiLocale,
            content = content,
        )
    }
}
