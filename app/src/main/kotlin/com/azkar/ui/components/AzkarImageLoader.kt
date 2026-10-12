package com.azkar.ui.components

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import io.github.vinceglb.filekit.coil.addPlatformFileSupport

/**
 * Teaches the app's single [ImageLoader] to read a FileKit file.
 *
 * **This is the only place in the app that knows Coil and FileKit are the same library.** They are
 * not, and nothing else should have to pretend they are: covers arrive as a `PlatformFile` because
 * that is what the picker hands back, and they leave for the loader as a `PlatformFile` because
 * that is what it knows how to fetch. Everything in between — the card, the dialog's preview, the
 * cache — works in cover *names*, which are strings, and which is the only currency any of the
 * other layers already had.
 *
 * Installed once from `AzkarTheme` rather than from the app shell. That is the seam that matters:
 * `AzkarTheme` is the one composable every root already passes through — the running app, every
 * `@Preview`, the off-screen screenshot harness and the UI tests — so a fourth place that grows one
 * later cannot forget this. A loader installed in `AzkarApp` would have left the previews throwing
 * `IllegalStateException: There is no ImageLoader set` the first time somebody previewed a card
 * with a cover on it.
 *
 * [addPlatformFileSupport] registers FileKit's keyer, mapper and fetcher in one call. Writing out
 * those three registrations by hand would be the same three lines a library has already written,
 * and would silently stop compiling the day FileKit changed which of them it needs.
 *
 * No `crossfade` and no disk cache are configured. The covers are already a copy in the app's own
 * directory, read once per card and kept for the life of the process, which is the workload Coil's
 * defaults are sized for; a fade would put a quarter of a second of blur between a card and the
 * picture the user picked, and a disk cache would be a second copy of files this app already owns.
 */
@Composable
internal fun InstallAzkarImageLoader() {
    setSingletonImageLoaderFactory { platformContext -> platformContext.imageLoader() }
}

/**
 * The loader itself, built from a [PlatformContext] Coil supplies.
 *
 * Kept as its own function rather than inlined into the factory because that lambda is not
 * composable, and because a builder call written inline in a lambda reads as configuration when it
 * is really the loader's whole definition.
 */
private fun PlatformContext.imageLoader(): ImageLoader =
    ImageLoader.Builder(this).components { addPlatformFileSupport() }.build()
