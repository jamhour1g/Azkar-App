package com.azkar.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.azkar.i18n.ENGLISH
import com.azkar.i18n.isRightToLeft
import com.azkar.ui.components.InstallAzkarImageLoader
import com.azkar.ui.theme.AzkarTheme
import java.util.Locale

val LocalAzkarColors = staticCompositionLocalOf { LightAzkarColors }

val LocalUiLocale = staticCompositionLocalOf { ENGLISH }

/** The single entry point every Azkar composable uses to read migrated CSS custom properties. */
object AzkarTheme {
    val colors: AzkarColors
        @Composable @ReadOnlyComposable
        get() = LocalAzkarColors.current

    val typography: androidx.compose.material3.Typography
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography

    /**
     * The corner-radius ramp, and the app's only source of truth for it.
     *
     * Screens used to spell `RoundedCornerShape(16.dp)` inline, which meant [AzkarShapes] was
     * installed into `MaterialTheme` and then never read by anything — a ramp that governed nothing.
     * Every radius now comes from here instead, so changing the curve below is a real, app-wide change.
     */
    val shapes: Shapes
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.shapes

    /**
     * Material 3 Expressive's spatial and effects specs, spring-based and deliberately spring-based.
     *
     * Note this does *not* govern [AzkarMotion]. That object hands Compose explicit tween specs on
     * bezier curves, and a caller who passes its own spec never consults the motion scheme — so this
     * only reaches the Material components that animate themselves (see [AzkarTheme] for the rest).
     */
    val motionScheme: MotionScheme
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.motionScheme

    /** The locale the shell resolved, so data-layer helpers can format against it. */
    val locale: Locale
        @Composable @ReadOnlyComposable
        get() = LocalUiLocale.current

    /**
     * The styleable properties of the components Material does not ship.
     *
     * A static reference rather than a `CompositionLocal`: the styles are a catalogue, not a value
     * that changes with the theme, and every entry reads the tokens it needs off the `StyleScope` at
     * apply time (see the `colors`/`typography`/`shapes` extensions in `ComponentStyles.kt`). Putting
     * them behind a local would add a read on every styled element to carry something that is the
     * same object in every theme.
     *
     * [AzkarTheme.colors] is what a *screen* reaches for. This is what a component that is still
     * hand-drawn reaches for instead of hard-coding a `background`, a shape or a `minHeight`.
     */
    val styles: ComponentStyles
        get() = ComponentStyles
}

/**
 * Projects [AzkarColors] onto the Material 3 scheme.
 *
 * This is the one place the two systems meet, and it exists so that app code has a single way to ask
 * for a colour — [AzkarTheme.colors] — while Material components, which read `MaterialTheme`
 * themselves, still see the app's palette instead of Material's default purple-tinted ramp. Naming
 * every slot explicitly is cheaper than auditing which components reach for which one.
 *
 * The slots app code never reads are still named, because "unused" is a property of *this* app's
 * screens rather than of every Material component, and a slot left to the baseline is the one that
 * shows up as a stray lilac the day a component starts using it. Where a slot has no honest answer
 * in the app's own palette it is left to the baseline on purpose — see [scrim].
 */
private fun toMaterialScheme(colors: AzkarColors): ColorScheme {
    val baseline = if (colors.isDark) darkColorScheme() else lightColorScheme()
    // `accent` is a bright amber in *both* palettes, so what sits on it is dark in both: the amber's
    // own ink in the light theme, and the near-black the dark theme already uses on every light fill.
    // Reading `textPrimary` here would put near-white on bright amber, which is what this used to do
    // in the dark theme — a slot no screen reads today, and one that would have failed silently.
    val onAccent = if (colors.isDark) colors.onPrimary else colors.accentInk
    return baseline.copy(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        primaryContainer = colors.highlight,
        onPrimaryContainer = colors.textPrimary,
        secondary = colors.secondary,
        onSecondary = colors.onPrimary,
        secondaryContainer = colors.highlight,
        onSecondaryContainer = colors.textPrimary,
        tertiary = colors.accent,
        onTertiary = onAccent,
        tertiaryContainer = colors.accent,
        onTertiaryContainer = onAccent,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.cardBackground,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.highlight,
        onSurfaceVariant = colors.textSecondary,
        surfaceContainerLowest = colors.background,
        surfaceContainerLow = colors.cardBackground,
        surfaceContainer = colors.cardBackground,
        surfaceContainerHigh = colors.cardBackground,
        surfaceContainerHighest = colors.highlight,
        surfaceTint = colors.primary,
        // A tooltip is the one overlay that is deliberately *not* drawn from the page's own colours:
        // it sits on top of cards, artwork and hover fills alike, so it has to be legible on all of
        // them at once. The scheme's own answer for that is the inverse pair, and these two slots were
        // the only ones left on the Material baseline — which is why a tooltip would have arrived as
        // a lilac box in a blue-and-amber app. Inverted means inverted in both directions here:
        // [textPrimary] is near-black on the light page and near-white on the dark one, so the pair
        // reads as a dark chip on the light theme and a light chip on the dark one, which is what a
        // tooltip has to do to float above either.
        inverseSurface = colors.textPrimary,
        inverseOnSurface = colors.cardBackground,
        outline = colors.border,
        outlineVariant = colors.border,
        // `scrim` is deliberately not overridden. It is the one slot the app has no opinion about: a
        // scrim dims what is behind a modal, so it wants to be a neutral dark in both palettes, and
        // both Material baselines already say black. It used to be set to `textPrimary`, which is
        // near-*white* in the dark theme — a white scrim over a dark window.
        error = colors.dialogValidation,
        onError = colors.onPrimary,
    )
}

/**
 * The two projected schemes, built once.
 *
 * [toMaterialScheme] is a pure function of a palette, and the app has exactly two palettes —
 * [LightAzkarColors] and [DarkAzkarColors] — so calling it as an inline argument to
 * `MaterialExpressiveTheme` rebuilt a fresh `ColorScheme` on *every* re-invocation of [AzkarTheme].
 *
 * That is not a cheap identity to lose. `MaterialTheme`'s scheme local is a `staticCompositionLocalOf`,
 * which does not track readers: a changed instance invalidates everything beneath the provider rather
 * than just the components that read it. After the move to Material 3 Expressive that "everything" is
 * far larger than it used to be, because every migrated control reads `MaterialTheme.colorScheme`
 * internally. A theme or locale change was paying a full-app recomposition on top of the work it
 * actually needed to do.
 *
 * This mirrors what `Type.kt` already does for typography, where `azkarTypography` resolves to a
 * prebuilt `AmiriTypography`/`InterTypography` singleton for exactly the same reason.
 */
private val LightMaterialScheme: ColorScheme = toMaterialScheme(LightAzkarColors)

private val DarkMaterialScheme: ColorScheme = toMaterialScheme(DarkAzkarColors)

/**
 * The motion scheme the app animates Material components with.
 *
 * `expressive()` is the spring-based scheme from Material 3 Expressive — bouncier spatial springs and
 * longer, softer effects springs than `standard()`, which is the point of adopting the look at all.
 *
 * The app's own [AzkarMotion] is deliberately *not* wired through here and never will be. Its KDoc
 * makes the case that Azkar's motion should be tween-on-bezier and hold still across refreshes, which
 * is a different opinion from the one this scheme expresses; anyone who hands Compose an explicit spec
 * overrides the scheme anyway, so mixing the two is a choice rather than an accident.
 */
private val AzkarMotionScheme: MotionScheme = MotionScheme.expressive()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AzkarTheme(
    darkTheme: Boolean = rememberSystemTheme().isDark,
    uiLocale: Locale = Locale.getDefault(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkAzkarColors else LightAzkarColors
    val layoutDirection = if (uiLocale.isRightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr

    // Not a theme decision -- the palette, the type and the shapes above are -- but it belongs to the
    // same seam. Coil's loader is a process-wide singleton, and `AzkarTheme` is the one composable
    // every root already runs through, so installing it here is what makes it impossible for a
    // preview, the screenshot harness or a UI test to compose a cover without one. See
    // `AzkarImageLoader` for what the loader is taught and why it lives here.
    InstallAzkarImageLoader()

    CompositionLocalProvider(
        LocalAzkarColors provides colors,
        LocalUiLocale provides uiLocale,
        LocalLayoutDirection provides layoutDirection,
    ) {
        MaterialExpressiveTheme(
            colorScheme = if (darkTheme) DarkMaterialScheme else LightMaterialScheme,
            motionScheme = AzkarMotionScheme,
            typography = azkarTypography(uiLocale),
            shapes = AzkarShapes,
            content = content,
        )
    }
}

/** Whether the tree is laid out right to left, i.e. whether the current locale is RTL. */
@Composable
fun isRtlLayout(): Boolean = LocalLayoutDirection.current == LayoutDirection.Rtl
