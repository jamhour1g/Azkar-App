package com.azkar.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.i18n.usesArabicTypeface
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.ui.text.platform.Font as DesktopFont

private const val AMIRI_REGULAR = "/com/azkar/fonts/Amiri/Amiri-Regular.ttf"
private const val AMIRI_BOLD = "/com/azkar/fonts/Amiri/Amiri-Bold.ttf"
private const val AMIRI_ITALIC = "/com/azkar/fonts/Amiri/Amiri-Italic.ttf"
private const val AMIRI_BOLD_ITALIC = "/com/azkar/fonts/Amiri/Amiri-BoldItalic.ttf"

private const val INTER_ROMAN = "/com/azkar/fonts/Inter/Inter-VariableFont_opsz,wght.ttf"
private const val INTER_ITALIC = "/com/azkar/fonts/Inter/Inter-Italic-VariableFont_opsz,wght.ttf"

/**
 * The optical size Inter is drawn at.
 *
 * Inter's `opsz` axis is not one value: the face is drawn differently at each size, with the larger
 * optical sizes tightening spacing and lifting the x-height for headline work. Following it per
 * text size is not possible here. Compose matches a font request on weight and style only, and Skiko
 * caches a typeface per resource path, weight and style while ignoring the variation settings
 * themselves — so two families reading the same file at different `opsz` would share one typeface
 * and the second size would be drawn with the first one's axis. Banding the axis would mean shipping
 * the variable font under a second resource path, which costs the binary twice to buy a difference
 * that is only really visible above 24sp.
 *
 * 18 is therefore pinned, which is the size the static `Inter_18pt-*` files this replaces were cut
 * at: every existing text size keeps the drawing it had before, and the italic coverage below is
 * fixed without anything else moving.
 */
private const val INTER_OPTICAL_SIZE = 18f

/**
 * Every weight Inter ships, lightest first.
 *
 * All nine are registered rather than the four the type scale mostly asks for, because
 * `FontFamily` snaps a request to the nearest registered weight: leaving `Thin` out would silently
 * draw it at `Light`, and `Black` at `ExtraBold`. One file and nine settings covers all of them,
 * which is the whole point of taking the variable font over the static cuts.
 */
private val INTER_WEIGHTS =
    listOf(
        FontWeight.Thin,
        FontWeight.ExtraLight,
        FontWeight.Light,
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
        FontWeight.ExtraBold,
        FontWeight.Black,
    )

private fun load(
    resource: String,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
) = DesktopFont(resource = resource, weight = weight, style = style)

/**
 * One Inter face, upright or italic, at a given weight.
 *
 * The weight and style are handed to `Settings` as well as to the font itself. The first pair is
 * what `FontFamily` matches a request against; the second is what Skia sets on the `wght` and `ital`
 * axes, and a variable font is drawn from its axes alone — a registration that declared a weight
 * without setting it would render every weight identically.
 */
private fun loadInter(
    resource: String,
    weight: FontWeight,
    style: FontStyle,
) = DesktopFont(
    resource = resource,
    weight = weight,
    style = style,
    variationSettings =
        FontVariation.Settings(
            weight,
            style,
            FontVariation.Setting("opsz", INTER_OPTICAL_SIZE),
        ),
)

/**
 * Latin family: mirrors the JavaFX `Noto Sans, Segoe UI, system-ui, sans-serif` stack.
 *
 * Both files are registered across all nine weights and both styles, so the italic translations and
 * citations keep their slant at whichever weight their card sets. The static cuts this replaced
 * shipped italic at two weights only, and every other italic silently fell back to upright Regular.
 */
private val InterFamily: FontFamily =
    FontFamily(
        buildList {
            for (weight in INTER_WEIGHTS) {
                add(loadInter(INTER_ROMAN, weight, FontStyle.Normal))
                add(loadInter(INTER_ITALIC, weight, FontStyle.Italic))
            }
        },
    )

/**
 * Arabic family: mirrors the JavaFX `Amiri, Noto Naskh Arabic, serif` stack.
 *
 * Still four static files rather than a variable font, because Amiri ships no variable cut — and
 * only the two weights it does have. A rung of the scale asking for `SemiBold` or `Medium` in
 * Arabic therefore clamps to `Bold` or `Regular`, which is the face Amiri actually draws and not a
 * fallback this app chose.
 */
private val AmiriFamily: FontFamily =
    FontFamily(
        load(AMIRI_REGULAR),
        load(AMIRI_BOLD, FontWeight.Bold),
        load(AMIRI_ITALIC, style = FontStyle.Italic),
        load(AMIRI_BOLD_ITALIC, FontWeight.Bold, FontStyle.Italic),
    )

/** One rung of the CSS type scale: size, weight and the Latin/Arabic leading heights. */
private data class TypeStep(
    val sizeSp: Int,
    val weight: FontWeight,
    val latinLeadingSp: Int,
    val arabicLeadingSp: Int,
)

private object AzkarTypeScale {
    val HEADLINE_LARGE = TypeStep(48, FontWeight.Bold, 56, 68)
    val HEADLINE_MEDIUM = TypeStep(32, FontWeight.Bold, 38, 46)
    val HEADLINE_SMALL = TypeStep(24, FontWeight.Bold, 30, 34)
    val TITLE_LARGE = TypeStep(20, FontWeight.Bold, 26, 30)
    val TITLE_MEDIUM = TypeStep(16, FontWeight.SemiBold, 22, 26)
    val TITLE_SMALL = TypeStep(14, FontWeight.SemiBold, 20, 22)
    val BODY_LARGE = TypeStep(18, FontWeight.Normal, 26, 32)
    val BODY_MEDIUM = TypeStep(16, FontWeight.Normal, 24, 28)
    val BODY_SMALL = TypeStep(14, FontWeight.Normal, 20, 24)
    val LABEL_LARGE = TypeStep(14, FontWeight.SemiBold, 20, 22)
    val LABEL_MEDIUM = TypeStep(12, FontWeight.Medium, 16, 20)
    val LABEL_SMALL = TypeStep(11, FontWeight.Medium, 16, 18)
}

private fun TextStyle.step(
    step: TypeStep,
    family: FontFamily,
    arabic: Boolean,
): TextStyle =
    copy(
        fontFamily = family,
        fontWeight = step.weight,
        fontSize = step.sizeSp.sp,
        lineHeight = if (arabic) step.arabicLeadingSp.sp else step.latinLeadingSp.sp,
    )

private fun azkarTypography(
    family: FontFamily,
    arabic: Boolean,
): Typography {
    val base = Typography()
    with(AzkarTypeScale) {
        return Typography(
            displayLarge = base.displayLarge.copy(fontFamily = family),
            displayMedium = base.displayMedium.copy(fontFamily = family),
            displaySmall = base.displaySmall.copy(fontFamily = family),
            headlineLarge = base.headlineLarge.step(HEADLINE_LARGE, family, arabic),
            headlineMedium = base.headlineMedium.step(HEADLINE_MEDIUM, family, arabic),
            headlineSmall = base.headlineSmall.step(HEADLINE_SMALL, family, arabic),
            titleLarge = base.titleLarge.step(TITLE_LARGE, family, arabic),
            titleMedium = base.titleMedium.step(TITLE_MEDIUM, family, arabic),
            titleSmall = base.titleSmall.step(TITLE_SMALL, family, arabic),
            bodyLarge = base.bodyLarge.step(BODY_LARGE, family, arabic),
            bodyMedium = base.bodyMedium.step(BODY_MEDIUM, family, arabic),
            bodySmall = base.bodySmall.step(BODY_SMALL, family, arabic),
            labelLarge = base.labelLarge.step(LABEL_LARGE, family, arabic),
            labelMedium = base.labelMedium.step(LABEL_MEDIUM, family, arabic),
            labelSmall = base.labelSmall.step(LABEL_SMALL, family, arabic),
        )
    }
}

private val InterTypography = azkarTypography(InterFamily, arabic = false)

private val AmiriTypography = azkarTypography(AmiriFamily, arabic = true)

/**
 * Ramps built for faces off the reader's system, keyed by `(family, script)`.
 *
 * Concurrent because the settings screen and the main window can both ask for a ramp in the same
 * frame, and a plain map would be a data race for the sake of a field read.
 */
private val systemTypographyCache = ConcurrentHashMap<Pair<String, Boolean>, Typography>()

/**
 * Replaces the JavaFX `-fx-font-family` overrides with a Compose typography tuned per script.
 *
 * This is the whole of what a new language costs on the typography side, because direction is
 * already answered in `com.azkar.i18n` and nothing here is per-language. What it does cost is a
 * *face*: `app/src/main/resources/com/azkar/fonts` holds Amiri and Inter and nothing else, so a
 * script Inter has no glyphs for — Devanagari, Thai, Burmese — draws as tofu here rather than
 * falling back, and adding that language means bundling its font next to this file. A language
 * written in Latin or Arabic script needs no font and lands on a cached instance below.
 *
 * Two faces and not a chain of them, so the second face in a `FontFamily` is not currently reachable
 * as a design decision: Amiri draws the Latin inside an Arabic line with its own Latin, exactly as
 * the JavaFX `Amiri, Noto Naskh Arabic, serif` stack it replaces did. Chain [AmiriFamily] onto
 * [InterFamily] to change that.
 */
fun azkarTypography(uiLocale: Locale): Typography =
    if (uiLocale.usesArabicTypeface) AmiriTypography else InterTypography

/**
 * Ramped typography for a face the reader chose off their own system.
 *
 * Keyed and cached by `(family, script)`. The cache is what makes this cheap to call from
 * `AzkarTheme` on every recomposition — building a `Typography` is not itself expensive, but a
 * fresh instance is handed to `MaterialTheme`, whose typography local is a `staticCompositionLocalOf`
 * and therefore invalidates every reading it anywhere in the tree. A new instance per frame would
 * redraw the app to change nothing.
 *
 * @see AppTypography for why the scale is not applied here.
 */
private fun systemTypography(
    family: String,
    arabic: Boolean,
): Typography =
    systemTypographyCache.getOrPut(family to arabic) {
        // A family this machine no longer has. The reader gets the bundled pairing rather than a
        // name Compose could not resolve: the settings screen has already shown the name as missing,
        // and a reminder that silently changed face would be the app disagreeing with itself.
        val chosen =
            SystemFontCatalogue.family(family)
                ?: return@getOrPut if (arabic) AmiriTypography else InterTypography
        azkarTypography(family = chosen, arabic = arabic)
    }

/**
 * The typography for a reader's typographic choice.
 *
 * The bundled pairing is still the answer for most readers and is reached without building anything:
 * [InterTypography] and [AmiriTypography] are the same two instances that were already here, so the
 * default path costs nothing at all.
 *
 * ## Why a face off the system needs nothing else arranged around it
 *
 * The obvious worry about handing Compose a desktop font is that a face with no Arabic in it will
 * draw tofu, and the obvious fix is a fallback list. There is no API for one here, and there turns
 * out not to be a need for one either — the two halves of that were worth checking properly:
 *
 * - `TextStyle` in this version of Compose has no `fontFamilyFallback` property. Android gained it
 *   later; this is a multiplatform `TextStyle`, and the field simply is not on it.
 * - A `FontFamily` does not chain for glyphs either. Compose matches a request to *one* `Font` for
 *   the requested weight and style and hands the resulting typeface to the paragraph; the second and
 *   later fonts in the list are consulted only if an earlier one fails to load at all, not if it
 *   turns out to lack a glyph.
 * - Neither is needed, because the fallback happens underneath Compose. Skiko builds the paragraph
 *   with no `FontCollection` of its own, which means Skia's default font collection is in play, and
 *   that one has glyph fallback switched on: a character the resolved face does not have is looked
 *   up in the system font manager, which on any machine with a fontconfig or a font directory has
 *   something for Arabic.
 *
 * So a reader's chosen face is used for every character it has, the desktop supplies the rest, and
 * the app does not have to pretend it can audit which of the several hundred installed families
 * carry which of the scripts this data set happens to contain.
 */
fun azkarTypography(
    uiLocale: Locale,
    typography: AppTypography,
): Typography {
    if (typography.isAzkarFont) return azkarTypography(uiLocale)
    return systemTypography(typography.family, uiLocale.usesArabicTypeface)
}

/** Gap/spacing tokens lifted from the JavaFX padding + spacing values in the FXML files. */
object AzkarSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val section = 26.dp
    val dashboard = 24.dp
    val screenPaddingHorizontal = 28.dp
    val screenPaddingTop = 24.dp
    val screenPaddingBottom = 32.dp
}
