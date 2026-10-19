package com.azkar.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.bell
import com.azkar.generated.resources.bell_off
import com.azkar.generated.resources.book_open
import com.azkar.generated.resources.bookmark
import com.azkar.generated.resources.brand
import com.azkar.generated.resources.calendar
import com.azkar.generated.resources.chevron_left
import com.azkar.generated.resources.chevron_right
import com.azkar.generated.resources.clock
import com.azkar.generated.resources.compass
import com.azkar.generated.resources.house
import com.azkar.generated.resources.khatim
import com.azkar.generated.resources.library
import com.azkar.generated.resources.moon
import com.azkar.generated.resources.mosque
import com.azkar.generated.resources.settings
import com.azkar.generated.resources.star
import com.azkar.generated.resources.sun
import com.azkar.generated.resources.sunrise
import com.azkar.generated.resources.sunset
import com.azkar.generated.resources.user
import org.jetbrains.compose.resources.vectorResource

/**
 * The Azkar set: the brand mark, the two mirrored chevrons, and composable accessors to the stroke
 * icons, which now ship as XML vector drawables in `composeResources/drawable`.
 *
 * The stroke icons are the adopted set — Lucide v0.544.0 (ISC) for bell, clock, calendar, house,
 * library, book-open, bookmark, star, settings, sun family and moon, plus the app's own khatim and
 * mosque. Every source SVG is versioned under `assets/icons` (upstream Lucide with its LICENSE, and
 * the `azkar` artwork), and each was converted to an XML `<vector>` whose geometry matches the code
 * that used to build them here. The conversion keeps the SVG contract — an empty fill and a black
 * stroke, instead of Lucide's CSS-only `currentColor` — so, as before, the icon has no colour of its
 * own and the one `tint` passed to `Icon` is the whole of its appearance. A 20dp chevron inside a
 * prayer row and the same chevron inside the 18dp dark promo header stay locked to their text.
 *
 * [vectorResource] hands each drawable back as an [ImageVector], so every consumer of this object
 * keeps its `ImageVector`-typed API and compiles unchanged.
 *
 * Adopting the set did move some geometry: the sun, sunrise, sunset, moon, star and settings gear
 * now use the newer upstream outlines (the app's were older forks of them), the common stroke
 * settled at Lucide's 2.0 where 1.8 was, and the app's open-book and home outlines gave way to the
 * upstream book-open and house. Three hairlines are the app's own and are kept — the khatim at 1.2,
 * the mosque at 1.7, the settings gear at 1.6 — because those widths are part of how the glyphs read.
 *
 * Nothing is built in code any more. [Brand] and the two chevrons used to be, on the grounds that the
 * XML vector format cannot carry a `Brush` gradient or `autoMirror`; both of those claims were wrong
 * for this stack, where [vectorResource] parses both.
 *
 * - [Brand] is a filled, self-coloured mark: it carries its own blue gradient, and an `Icon` tint
 *   would flatten it to a single colour, which is why it is drawn with `Image`. The gradient now
 *   rides in an inline `<aapt:attr>` element in `drawable/brand.xml`.
 * - [ChevronRight] and [ChevronStart] are `autoMirror` — they flip with the layout direction so a
 *   "next" affordance points the way the reader is already travelling in either script — which
 *   `drawable/chevron_right.xml` and `chevron_left.xml` both declare.
 *
 * Every outline in this set is read verbatim out of an SVG `d` attribute, because a hand-transcribed
 * `moveTo/curveTo` chain is a second chance to introduce a typo into artwork nobody here can draw by
 * hand. The brand's own fit — the group that scales and centres it inside its viewport — is recorded
 * as a comment on the drawable that carries it.
 *
 * What left this set: dismiss. The X is generic chrome, so it lives in [ActionIcons.dismiss] — the
 * brand set's small tailored cross was traded for the material glyph, accepted at the sizes that
 * button is ever drawn at.
 */
object AzkarIcons {
    /**
     * The app mark: a gradient eight-pointed star (the khatim) with a white mosque inside it.
     *
     * Drawn with `Image` rather than `Icon` throughout, because it carries its own colours — an
     * `Icon` tint would flatten the gradient. The drawable fits its own artwork to its viewport,
     * which is what the tray depends on: it draws this into a fixed square and is downscaled to a
     * 24px Linux tray slot, where a mark filling only 70% of its viewport lands both small and high.
     */
    val Brand: ImageVector
        @Composable get() = vectorResource(Res.drawable.brand)

    /** The khatim on its own, stroked hairline — the watermark behind the azkar card's text. */
    val Khatim: ImageVector
        @Composable get() = vectorResource(Res.drawable.khatim)

    /** Fajr's horizon, or a plain mosque wherever the context is "the app", not "a prayer". */
    val Mosque: ImageVector
        @Composable get() = vectorResource(Res.drawable.mosque)

    /** Dhuhr and Asr: the sun at its height. */
    val Sun: ImageVector
        @Composable get() = vectorResource(Res.drawable.sun)

    /** Fajr: a sun resting on its horizon, rays above it. */
    val Sunrise: ImageVector
        @Composable get() = vectorResource(Res.drawable.sunrise)

    /** Maghrib: the same sun, dropped below its line. */
    val Sunset: ImageVector
        @Composable get() = vectorResource(Res.drawable.sunset)

    /** Isha, and the header's dark-mode toggle. */
    val Moon: ImageVector
        @Composable get() = vectorResource(Res.drawable.moon)

    val Bell: ImageVector
        @Composable get() = vectorResource(Res.drawable.bell)

    /**
     * [Bell] with a slash, so "alerts are off" never reads as "alerts".
     *
     * Two glyphs, not one tinted differently: state the reader has to act on deserves a visible
     * change of shape, not the same bell whose only difference is colour — and the slash is exactly
     * how the rest of the app says "off".
     */
    val BellOff: ImageVector
        @Composable get() = vectorResource(Res.drawable.bell_off)

    val Clock: ImageVector
        @Composable get() = vectorResource(Res.drawable.clock)

    val Calendar: ImageVector
        @Composable get() = vectorResource(Res.drawable.calendar)

    /** An enclosed house with a door — Lucide's `house`, for everything that says "home". */
    val Home: ImageVector
        @Composable get() = vectorResource(Res.drawable.house)

    /** Standing book spines — Lucide's `library`, used where the context is the library's contents. */
    val Library: ImageVector
        @Composable get() = vectorResource(Res.drawable.library)

    /**
     * The open book the brand uses everywhere a piece of dhikr is offered.
     *
     * Lucide's `book-open`. The asset pack's own open form had rounded page covers; the upstream
     * outline splits into two crisp leaves, which at the 34dp glyph on the azkar tile keeps the
     * spine and the page ends distinct.
     */
    val BookOpen: ImageVector
        @Composable get() = vectorResource(Res.drawable.book_open)

    val Bookmark: ImageVector
        @Composable get() = vectorResource(Res.drawable.bookmark)

    val Star: ImageVector
        @Composable get() = vectorResource(Res.drawable.star)

    /**
     * The gear, at the app's 1.6 stroke.
     *
     * Lucide's `settings`. The asset pack's own gear mixed arc radii that collapsed into a lopsided
     * blob at 20dp button size; the upstream contour keeps a uniform curve on every lobe, so it
     * stays a cog at button size — it did when this was a code-built fork of it, and it still does.
     */
    val Settings: ImageVector
        @Composable get() = vectorResource(Res.drawable.settings)

    /** Lucide's `user`: the head and shoulders standing for a profile. */
    val Profile: ImageVector
        @Composable get() = vectorResource(Res.drawable.user)

    val Compass: ImageVector
        @Composable get() = vectorResource(Res.drawable.compass)

    /**
     * Flips with the layout direction, so a "next" affordance points the way the reader is already
     * travelling instead of always to the right.
     */
    val ChevronRight: ImageVector
        @Composable get() = vectorResource(Res.drawable.chevron_right)

    /**
     * The other half of [ChevronRight], for a control that walks back the way the reader came.
     *
     * A mirrored copy of that one rather than a rotated one, because a rotation cannot flip: both
     * ends of a row have to point the way the reader is already travelling — one way in a left-to-
     * right layout and the other in a right-to-left one — and only a pair that each carry their own
     * mirroring manages that in both.
     */
    val ChevronStart: ImageVector
        @Composable get() = vectorResource(Res.drawable.chevron_left)
}
