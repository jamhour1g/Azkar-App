package com.azkar.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Azkar brand set, ported from the hand-drawn SVGs in `azkar-compose-ui-assets`.
 *
 * The SVGs paint with `currentColor`, which is a CSS-only idea. Every icon here is built the same
 * way — an outline vector with no colour of its own — so the *only* thing that decides how an icon
 * looks is the `tint` passed to `Icon`. That keeps a 20dp chevron inside a prayer row and the same
 * chevron inside the 18dp dark promo header in lockstep with their surrounding text.
 *
 * Geometry is parsed straight out of the SVG `d` attributes rather than re-expressed as builder
 * calls: the outlines are already exact, and a hand-transcribed `moveTo/curveTo` chain is a second
 * chance to introduce a typo into artwork nobody here can draw by hand. A circle has no `d`
 * equivalent, so it is expanded to the usual two-arc path by [circle].
 *
 * The exception is [Brand]. It is a filled, self-coloured mark, so it carries its own gradient and
 * must be drawn with `Image` — `Icon`'s tint would flatten it to a single colour.
 */
object AzkarIcons {
    /** The app mark: a gradient eight-pointed star (the khatim) with a white mosque inside it. */
    val Brand: ImageVector =
        ImageVector
            .Builder(
                name = "Azkar.Brand",
                defaultWidth = BRAND_SIZE,
                defaultHeight = BRAND_SIZE,
                viewportWidth = BRAND_VIEWPORT,
                viewportHeight = BRAND_VIEWPORT,
            ).apply {
                addPath(pathData = nodes(BRAND_STAR), fill = BRAND_FILL)
                addPath(pathData = nodes(MOSQUE_CROWN_PATH), fill = SolidColor(Color.White))
                addPath(pathData = nodes(MOSQUE_DOME_PATH), fill = SolidColor(Color.White))
                addPath(pathData = nodes("M15.35 4.2h1.3v1.9h-1.3z"), fill = SolidColor(Color.White))
                addPath(pathData = nodes("M14.2 20.2h3.6v-3.1h-3.6z"), fill = SolidColor(Color.White))
            }.build()

    /** The khatim on its own, stroked hairline — the watermark behind the azkar card's text. */
    val Khatim: ImageVector =
        outline(
            name = "Azkar.Khatim",
            strokes =
                listOf(
                    Stroke(KHATIM_OUTER, KHATIM_HAIRLINE),
                    Stroke(KHATIM_INNER, KHATIM_HAIRLINE),
                    Stroke(circle(cx = 40f, cy = 40f, r = 5f), KHATIM_HAIRLINE),
                ),
            size = 80.dp,
            viewport = 80f,
        )

    /** Fajr's horizon, or a plain mosque wherever the context is "the app", not "a prayer". */
    val Mosque: ImageVector = outline("Azkar.Mosque", listOf(Stroke(MOSQUE_PATH, 1.7f)))

    /** Dhuhr and Asr: the sun at its height. */
    val Sun: ImageVector =
        outline(
            name = "Azkar.Sun",
            strokes =
                listOf(
                    Stroke(circle(cx = 12f, cy = 12f, r = 4f), 1.8f),
                    Stroke(SUN_RAYS, 1.8f),
                ),
        )

    /** Fajr: a sun resting on its horizon, rays above it. */
    val Sunrise: ImageVector = outline("Azkar.Sunrise", listOf(Stroke(SUNRISE_PATH, 1.8f)))

    /** Maghrib: the same sun, dropped below its line. */
    val Sunset: ImageVector = outline("Azkar.Sunset", listOf(Stroke(SUNSET_PATH, 1.8f)))

    /** Isha, and the header's dark-mode toggle. */
    val Moon: ImageVector = outline("Azkar.Moon", listOf(Stroke(MOON_PATH, 1.8f)))

    val Bell: ImageVector = outline("Azkar.Bell", listOf(Stroke(BELL_PATH, 1.8f)))

    /** [Bell] with a slash, so "alerts are off" never reads as "alerts". */
    val BellOff: ImageVector =
        outline(
            name = "Azkar.BellOff",
            strokes = listOf(Stroke(BELL_PATH, 1.8f), Stroke(BELL_SLASH, 1.8f)),
        )

    val Clock: ImageVector =
        outline(
            name = "Azkar.Clock",
            strokes =
                listOf(
                    Stroke(circle(cx = 12f, cy = 12f, r = 9f), 1.8f),
                    Stroke(CLOCK_HANDS, 1.8f),
                ),
        )

    val Calendar: ImageVector =
        outline(
            name = "Azkar.Calendar",
            strokes =
                listOf(
                    Stroke(CALENDAR_BODY, 1.8f),
                    Stroke(CALENDAR_RINGS, 1.8f),
                ),
        )

    val Home: ImageVector = outline("Azkar.Home", listOf(Stroke(HOME_PATH, 1.8f)))

    /** A closed book on a shelf — used where the context is the library's *contents*. */
    val Library: ImageVector =
        outline(
            name = "Azkar.Library",
            strokes =
                listOf(
                    Stroke("M4 5.5A2.5 2.5 0 0 1 6.5 3H20v17H6.5A2.5 2.5 0 0 0 4 22V5.5Z", 1.8f),
                    Stroke("M4 5.5V19", 1.8f),
                ),
        )

    /**
     * The open book the brand uses everywhere a piece of dhikr is offered.
     *
     * The asset pack's `library.svg` is a *closed* book, which is right for a shelf but reads as a
     * blank rounded rectangle once it is scaled up to the 34dp glyph on the azkar tile. This is the
     * open form from the reference design: two pages meeting at a spine, drawn as an outline so it
     * keeps the same 24px / 1.8px stroke as the rest of the set.
     */
    val BookOpen: ImageVector =
        outline(
            name = "Azkar.BookOpen",
            strokes =
                listOf(
                    Stroke(BOOK_OPEN_PAGES, 1.8f),
                    Stroke(BOOK_SPINE, 1.8f),
                ),
        )

    val Bookmark: ImageVector = outline("Azkar.Bookmark", listOf(Stroke(BOOKMARK_PATH, 1.8f)))

    val Star: ImageVector = outline("Azkar.Star", listOf(Stroke(STAR_PATH, 1.8f)))

    val Settings: ImageVector =
        outline(
            name = "Azkar.Settings",
            strokes =
                listOf(
                    Stroke(circle(cx = 12f, cy = 12f, r = 3f), 1.8f),
                    Stroke(SETTINGS_GEAR, 1.6f),
                ),
        )

    val Profile: ImageVector =
        outline(
            name = "Azkar.Profile",
            strokes =
                listOf(
                    Stroke(circle(cx = 12f, cy = 8f, r = 3.5f), 1.8f),
                    Stroke("M5 21a7 7 0 0 1 14 0", 1.8f),
                ),
        )

    val Compass: ImageVector =
        outline(
            name = "Azkar.Compass",
            strokes =
                listOf(
                    Stroke(circle(cx = 12f, cy = 12f, r = 9f), 1.8f),
                    Stroke(COMPASS_NEEDLE, 1.8f),
                ),
        )

    /**
     * Flips with the layout direction, so a "next" affordance points the way the reader is already
     * travelling instead of always to the right.
     */
    val ChevronRight: ImageVector =
        ImageVector
            .Builder(
                name = "Azkar.ChevronRight",
                defaultWidth = ICON_SIZE,
                defaultHeight = ICON_SIZE,
                viewportWidth = ICON_VIEWPORT,
                viewportHeight = ICON_VIEWPORT,
                autoMirror = true,
            ).apply {
                addPath(
                    pathData = nodes(CHEVRON_PATH),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }.build()

    /**
     * The other half of [ChevronRight], for a control that walks back the way the reader came.
     *
     * A mirrored copy of that one rather than a rotated one, because a rotation cannot flip: both
     * ends of a row have to point the way the reader is already travelling — one way in a left-to-
     * right layout and the other in a right-to-left one — and only a pair that each carry their own
     * mirroring manages that in both.
     */
    val ChevronStart: ImageVector =
        ImageVector
            .Builder(
                name = "Azkar.ChevronStart",
                defaultWidth = ICON_SIZE,
                defaultHeight = ICON_SIZE,
                viewportWidth = ICON_VIEWPORT,
                viewportHeight = ICON_VIEWPORT,
                autoMirror = true,
            ).apply {
                addPath(
                    pathData = nodes(CHEVRON_BACK_PATH),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }.build()
}

/** One stroked sub-path of an outline icon, plus the width that particular path uses. */
private class Stroke(
    val data: String,
    val width: Float,
)

private const val ICON_VIEWPORT = 24f
private const val BRAND_VIEWPORT = 32f
private const val KHATIM_HAIRLINE = 1.2f
private val ICON_SIZE = 24.dp
private val BRAND_SIZE = 32.dp

private val BRAND_FILL =
    Brush.linearGradient(
        colors = listOf(Color(0xFF60A5FA), Color(0xFF2563EB)),
        start = Offset(4f, 2f),
        end = Offset(28f, 30f),
    )

private const val BRAND_STAR =
    "M16 1.8 19.4 5l4.6-.1.1 4.6 3.2 3.4-3.2 3.4.1 4.6-4.6-.1-3.4 3.2-3.4-3.2-4.6.1.1-4.6L5 12.9" +
        "l3.2-3.4-.1-4.6 4.6.1L16 1.8Z"

private const val MOSQUE_PATH = "M3 20h18M5 20v-7h14v7M7 13V9l5-4 5 4v4M10 20v-4h4v4M12 3v3"
private const val MOSQUE_CROWN_PATH =
    "M10 20.2v-5.4l2-2.1v7.5h2v-9.9l2-2.2 2 2.2v9.9h2v-7.5l2 2.1v5.4H10Z"
private const val MOSQUE_DOME_PATH = "M16 6.1c1.1 0 2 .9 2 2h-4c0-1.1.9-2 2-2Z"

private const val KHATIM_OUTER =
    "M40 5 52 17 69 11 63 28 75 40 63 52 69 69 52 63 40 75 28 63 11 69 17 52 5 40 17 28 11 11 28 17Z"
private const val KHATIM_INNER = "M40 17 49 31 63 40 49 49 40 63 31 49 17 40 31 31Z"

private const val SUN_RAYS =
    "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2" +
        "M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"

/**
 * Fajr's geometry: a half-sun resting on the horizon with its rays clear of the disc.
 *
 * The arc and the horizon are one continuous stroke, which is what makes the disc read as *sitting
 * on* the line rather than hovering above it.
 *
 * The rays are the part that has to be placed, not just mirrored. They start at y=15.1 while the
 * disc's flanks are at x=8.3 and x=15.7 at that height, so a 1.4-long ray from x=4.9 stops at
 * x=6.3 — clear of the disc by two units. Crowding them in is what turned the pair into a smudge
 * when these were first drawn.
 */
private const val SUNRISE_PATH =
    "M3 18h18M7.5 18a4.5 4.5 0 0 1 9 0M12 11.5V8.5M4.9 15.1l1.4-1.4M19.1 15.1l-1.4-1.4"

/**
 * Maghrib's geometry: [SUNRISE_PATH] mirrored through the box's horizontal centre.
 *
 * Mirroring a 24-unit box is mechanical and worth writing down, because the two details that are
 * easy to get wrong are both invisible until it renders: every `y` becomes `24 - y`, and the arc's
 * sweep flag inverts, because a reflection reverses winding. The first attempt at this glyph skipped
 * the sweep inversion, which put the disc back above the horizon — Maghrib drawn as a second Fajr.
 */
private const val SUNSET_PATH =
    "M3 6h18M7.5 6a4.5 4.5 0 0 0 9 0M12 12.5v3M4.9 8.9l1.4 1.4M19.1 8.9l-1.4 1.4"

private const val MOON_PATH = "M20.5 15.2A8.5 8.5 0 0 1 8.8 3.5 8.5 8.5 0 1 0 20.5 15.2Z"

private const val BELL_PATH = "M18 9a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9ZM10 21h4"
private const val BELL_SLASH = "M4 4l16 16"

private const val CLOCK_HANDS = "M12 7v5l3 2"
private const val CALENDAR_BODY =
    "M5.5 5h13A2.5 2.5 0 0 1 21 7.5v11a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 18.5v-11A2.5 2.5 0 0 1 5.5 5Z"
private const val CALENDAR_RINGS = "M16 3v4M8 3v4M3 10h18"

private const val HOME_PATH = "m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1V10Z"

private const val BOOK_OPEN_PAGES =
    "M12 7.4C10.8 5.7 8.9 4.8 6.4 4.8H3.8a1 1 0 0 0-1 1v10.4a1 1 0 0 0 1 1h2.6c2.2 0 4.1.8 5.6 2.2" +
        "1.5-1.4 3.4-2.2 5.6-2.2h2.6a1 1 0 0 0 1-1V5.8a1 1 0 0 0-1-1h-2.6C15.1 4.8 13.2 5.7 12 7.4Z"
private const val BOOK_SPINE = "M12 7.4v12"

private const val BOOKMARK_PATH = "M6 4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v18l-6-3.5L6 22V4Z"
private const val STAR_PATH = "m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-2.9-5.6 2.9 1.1-6.2L3 9.6l6.2-.9L12 3Z"

/**
 * The gear body: eight rounded lobes walked as one closed contour.
 *
 * Transcribed from Lucide's `settings` rather than the asset pack's own `icons/settings.svg`. The
 * pack's gear mixes six 1.7-radius arcs with a 1.3 stroke, which at a 20dp button size collapses
 * into a lopsided blob with one tooth reading as detached — it needs roughly 32dp before the teeth
 * separate. This contour keeps a uniform radius on every lobe, so it stays a cog at button size.
 */
private const val SETTINGS_GEAR =
    "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73" +
        "l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73" +
        "l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0" +
        " 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73" +
        "l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73" +
        "l-.15.08a2 2 0 0 1-2 0l-.43.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"

private const val COMPASS_NEEDLE = "m15.5 8.5-2 5-5 2 2-5 5-2Z"
private const val CHEVRON_PATH = "m9 6 6 6-6 6"

/** The same chevron walked the other way, for [AzkarIcons.ChevronStart]. */
private const val CHEVRON_BACK_PATH = "m15 6-6 6 6 6"

private fun outline(
    name: String,
    strokes: List<Stroke>,
    size: Dp = ICON_SIZE,
    viewport: Float = ICON_VIEWPORT,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = size,
            defaultHeight = size,
            viewportWidth = viewport,
            viewportHeight = viewport,
        ).apply {
            strokes.forEach { stroke ->
                addPath(
                    pathData = nodes(stroke.data),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = stroke.width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

/** A circle as a two-arc path — SVG's `<circle>` has no equivalent in the vector format. */
private fun circle(
    cx: Float,
    cy: Float,
    r: Float,
): String =
    buildString {
        append("M").append(cx - r).append(' ').append(cy)
        append("a")
            .append(r)
            .append(' ')
            .append(r)
            .append(" 0 1 0 ")
            .append(2 * r)
            .append(" 0")
        append("a")
            .append(r)
            .append(' ')
            .append(r)
            .append(" 0 1 0 ")
            .append(-2 * r)
            .append(" 0")
        append('Z')
    }

/**
 * Parses an SVG `d` attribute into vector nodes.
 *
 * A fresh [PathParser] per call: the parser reuses its node list between calls, and these vectors
 * are built from an object whose initialisation any two threads could race for.
 */
private fun nodes(pathData: String): List<PathNode> = PathParser().parsePathString(pathData).toNodes()
