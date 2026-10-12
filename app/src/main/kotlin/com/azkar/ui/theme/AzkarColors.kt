package com.azkar.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The app's palette: every colour a screen is allowed to reach for.
 *
 * These were the custom properties in the JavaFX stylesheet, one Kotlin field per declaration, and
 * the set has been pruned to the ones actually read. It carried sixty-six tokens; sixteen are gone —
 * most of the `.library-reminder-*` family, the soft half of the notification-priority ramp, and the
 * `.library-toast` gradient stops' unused middle. Each had been superseded by a local decision where
 * it was used: the reminder panel picks its own gradients where it is composed, and the priority
 * colours that survive are the two something still asks for by name.
 *
 * The two palettes are named by intent rather than by shade, which is what lets one name mean
 * different values in each — see [primaryStrong] and [accentInk], where the light and dark answers
 * differ because the surrounding surface does.
 *
 * App code reads these through [AzkarTheme.colors]. Material 3 components read
 * `MaterialTheme.colorScheme` instead, and [toMaterialScheme] is the one seam between the two —
 * there is no third place a colour is decided.
 */
@Immutable
data class AzkarColors(
    val primary: Color,
    /**
     * The same hue as [primary], darkened until it clears 4.5:1 on the light surfaces.
     *
     * [primary] is a fill colour — it backs buttons and badges, where the contrast requirement is
     * the one for a graphic. The moment it becomes *text* on a pale surface it stops being legible,
     * so every text and small-icon use in the light theme reads this instead.
     */
    val primaryStrong: Color,
    val onPrimary: Color,
    val secondary: Color,
    val background: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    /** The pale wash behind a selected row or an emphasised block. */
    val highlight: Color,
    val cardBackground: Color,
    val border: Color,
    val accent: Color,
    /**
     * The same hue as [accent], darkened until it clears 4.5:1 on the warm surfaces it sits on.
     *
     * The favourites card puts amber to work as a fill — a count pill, a star, a watermark — and
     * [accent] itself is unreadable as type on those tints. Same split as [primaryStrong]: the fill
     * colour and the ink colour are not the same colour.
     */
    val accentInk: Color,
    /** An opaque neutral for hover fills that should cover what is underneath. */
    val hoverNeutral: Color,
    /** A translucent neutral for hover and pressed states that must not shift the layout. */
    val hoverNeutralSoft: Color,
    val switchTrackOff: Color,
    val switchTrackOffHover: Color,
    val switchThumb: Color,
    val switchThumbBorder: Color,
    val browseIcon: Color,
    val favoritesNavBackground: Color,
    val favoritesNavText: Color,
    /**
     * The ink for a destructive or invalid value.
     *
     * Named for where it was first needed rather than for what it is, because that is still the only
     * place it appears; it is projected onto the scheme's `error` slot so a Material component asking
     * for the error colour gets this one rather than Material's own default red.
     */
    val dialogValidation: Color,
    val priorityHigh: Color,
    val priorityLow: Color,
    val toastHighStart: Color,
    val toastHighEnd: Color,
    val toastMediumStart: Color,
    val toastMediumEnd: Color,
    val toastLowStart: Color,
    val toastLowEnd: Color,
    val reminderSection: Color,
    val hadithEnglish: Color,
    val hadithSource: Color,
    /**
     * The wash laid over the hero illustration so the greeting stays readable.
     *
     * A pair rather than one colour: the artwork runs from a bright sky at the top to a dark
     * cityscape at the bottom, and a single flat scrim either leaves the skyline muddy or greys out
     * the sky. Both stops are the app's own [background], so the band dissolves into the page at
     * the leading edge and keeps the illustration's own colour everywhere else.
     */
    val heroScrimStart: Color,
    val heroScrimEnd: Color,
    /** The "Today's Azkar" card's pale blue wash, which also carries the geometric watermark. */
    val azkarCardStart: Color,
    val azkarCardEnd: Color,
    /**
     * The "Favorite Azkar" card's warm wash, which carries the same geometric watermark.
     *
     * A second wash rather than a recolour of [azkarCardStart]/[azkarCardEnd] because the two cards
     * sit side by side in one rail and say different things: one reports the day, the other is the
     * user's own pick. Amber keeps the favourites card in the same family — same corner radius, same
     * khatim texture — while reading as its own object.
     *
     * In the light palette the wash is only allowed to travel as far as #FFF8EC, and in practice
     * stops short of it: [textSecondary] and [hadithSource] share one grey and sit on top of this
     * gradient, and the most saturated amber that still clears 4.5:1 for them is barely warmer than
     * the page behind the card. So in the light palette the amber is carried by the card's border,
     * its watermark and its count pill instead, all of which are decoration and therefore free to
     * be as saturated as the eye likes. The wash's job there is warmth, not identity.
     */
    val favoriteCardStart: Color,
    val favoriteCardEnd: Color,
    /** Base tint of the geometric tile drawn behind the favourites card text. */
    val favoritePatternTint: Color,
    /** The wash's ink counterpart: the favourites card draws its 1dp edge in this, not in [border]. */
    val favoriteCardBorder: Color,
    /** The brand gradient behind the app mark, the azkar tile and the avatar. */
    val brandTileStart: Color,
    val brandTileEnd: Color,
    /** The row tint that marks the prayer the countdown is running towards. */
    val nextPrayerSurface: Color,
    /** Base tint of the Islamic geometric tile drawn behind the azkar card text. */
    val patternTint: Color,
    /**
     * The chip colour for a small label printed over an illustration, and the one pair in this theme
     * that is fixed to dark-on-light in *both* palettes.
     *
     * A badge says something about the collection rather than offering it — who published it — and it
     * sits in the corner of a painted photograph where half of it may be a bright sky. The pill below
     * gets away with following the theme because it is interactive and therefore has a state to be
     * read against; a badge has none, so there is nothing to anchor it but the artwork, and the only
     * ink that survives every artwork the app ships in both palettes is near-white on near-black glass.
     */
    val artBadgeSurface: Color,
    /** The label and glyph colour that sits on [artBadgeSurface]. Fixed light in both palettes. */
    val artBadgeInk: Color,
    /**
     * The fill of the hero's light action pills.
     *
     * A pill drawn over artwork cannot borrow the card palette, because the thing underneath it is a
     * photograph-like wash whose brightness is chosen by the artwork and not by us — a chip filled with
     * [cardBackground] reads as a hole punched in the picture on a dark illustration and as a smear
     * of grey on a pale one. So the pills are given their own surface and their own ink, and the two
     * are the only pair in the theme that is deliberately *not* derived from the page: a pill is a
     * piece of the artwork's own world, and the artwork is the one thing on this screen the light and
     * dark palettes agree on being dark.
     *
     * Translucent so the artwork still shows through and the three pills read as one row rather than
     * three boxes.
     *
     * Light in the light theme and dark in the dark one, which is the one place on this page where
     * "light" and "dark" are not the same thing: a near-white pill is a highlight on a dusk
     * photograph in either theme, but on a night photograph it is a searchlight. So the pill is glass
     * of the *current* theme and [artPillInk] is whatever is legible on glass of the current theme,
     * rather than the pair being fixed to white-on-dark for both.
     */
    val artPillSurface: Color,
    /**
     * The label and glyph colour that sits on [artPillSurface].
     *
     * Its own field rather than a reuse of [artPatternTint]'s neighbours because the pill answers to
     * the pill's own brightness: dark ink on the light pill of the light theme, light ink on the dark
     * pill of the dark one. Pairing one with the other's ink is how a row of pills ends up printing
     * itself in white on white.
     */
    val artPillInk: Color,
    /**
     * Base tint of the geometric khatim drawn over an illustration.
     *
     * Separate from [patternTint] because that one is drawn on the page's own background, where a
     * blue tint is what makes the texture legible, whereas over artwork it has to be near-neutral:
     * a saturated tint on top of a wash the artwork already coloured reads as a rendering fault
     * rather than as ornament.
     */
    val artPatternTint: Color,
    val isDark: Boolean,
)

internal val LightAzkarColors =
    AzkarColors(
        primary = Color(0xFF3B82F6),
        primaryStrong = Color(0xFF2563EB),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF6366F1),
        background = Color(0xFFF8FAFC),
        textPrimary = Color(0xFF0F172A),
        textSecondary = Color(0xFF64748B),
        highlight = Color(0xFFE8F1FF),
        cardBackground = Color(0xFFFFFFFF),
        border = Color(0xFFE2E8F0),
        accent = Color(0xFFF59E0B),
        accentInk = Color(0xFF92400E),
        hoverNeutral = Color(0xFFF3F4F6),
        hoverNeutralSoft = Color(0x1A000000),
        switchTrackOff = Color(0xFFE5E7EB),
        switchTrackOffHover = Color(0xFFD1D5DB),
        switchThumb = Color(0xFFFFFFFF),
        switchThumbBorder = Color(0xFFD1D5DB),
        browseIcon = Color(0xFF2563EB),
        favoritesNavBackground = Color(0xFFEFF3F8),
        favoritesNavText = Color(0xFF4B5563),
        dialogValidation = Color(0xFFB22525),
        priorityHigh = Color(0xFFA92424),
        priorityLow = Color(0xFF1F6B4A),
        toastHighStart = Color(0xFFFFF4F4),
        toastHighEnd = Color(0xFFFFF9F9),
        toastMediumStart = Color(0xFFFFFAF0),
        toastMediumEnd = Color(0xFFFFFDF6),
        toastLowStart = Color(0xFFF3FDF8),
        toastLowEnd = Color(0xFFF8FFFC),
        reminderSection = Color(0xC7FFFFFF),
        hadithEnglish = Color(0xFF64748B),
        hadithSource = Color(0xFF64748B),
        heroScrimStart = Color(0xF2F8FAFC),
        heroScrimEnd = Color(0x00F8FAFC),
        azkarCardStart = Color(0xFFEAF2FF),
        azkarCardEnd = Color(0xFFFFFFFF),
        favoriteCardStart = Color(0xFFFFF9F1),
        favoriteCardEnd = Color(0xFFFFFFFF),
        favoritePatternTint = Color(0xFFB45309),
        favoriteCardBorder = Color(0x33B45309),
        brandTileStart = Color(0xFF60A5FA),
        brandTileEnd = Color(0xFF2563EB),
        nextPrayerSurface = Color(0xFFC7DBFF),
        patternTint = Color(0xFF3B82F6),
        artBadgeSurface = Color(0xA60F172A),
        artBadgeInk = Color(0xFFF8FAFC),
        artPillSurface = Color(0xF2FFFFFF),
        artPillInk = Color(0xFF0F172A),
        artPatternTint = Color(0x26FFFFFF),
        isDark = false,
    )

internal val DarkAzkarColors =
    AzkarColors(
        primary = Color(0xFF60A5FA),
        primaryStrong = Color(0xFF93C5FD),
        onPrimary = Color(0xFF0B1220),
        secondary = Color(0xFFA5B4FC),
        background = Color(0xFF0B1220),
        textPrimary = Color(0xFFE8EDF7),
        textSecondary = Color(0xFF9AA8C4),
        highlight = Color(0xFF1B2942),
        cardBackground = Color(0xFF121B2E),
        border = Color(0xFF22304C),
        accent = Color(0xFFFBBF24),
        accentInk = Color(0xFFFCD34D),
        hoverNeutral = Color(0xFF2C2C2C),
        hoverNeutralSoft = Color(0x14FFFFFF),
        switchTrackOff = Color(0xFF2C2C2C),
        switchTrackOffHover = Color(0xFF374151),
        switchThumb = Color(0xFFF8FAFC),
        switchThumbBorder = Color(0xFF475569),
        browseIcon = Color(0xFF93C5FD),
        favoritesNavBackground = Color(0xFF1F2937),
        favoritesNavText = Color(0xFFD1D5DB),
        dialogValidation = Color(0xFFF08A8A),
        priorityHigh = Color(0xFFEE9A9A),
        priorityLow = Color(0xFF8ED4B2),
        toastHighStart = Color(0xFF2A1B1B),
        toastHighEnd = Color(0xFF2C1F1F),
        toastMediumStart = Color(0xFF2A2519),
        toastMediumEnd = Color(0xFF2C281D),
        toastLowStart = Color(0xFF18251F),
        toastLowEnd = Color(0xFF1B2622),
        reminderSection = Color(0xC71E2229),
        hadithEnglish = Color(0xFF9AA8C4),
        hadithSource = Color(0xFF93A0B8),
        heroScrimStart = Color(0xF20B1220),
        heroScrimEnd = Color(0x000B1220),
        azkarCardStart = Color(0xFF182440),
        azkarCardEnd = Color(0xFF101827),
        favoriteCardStart = Color(0xFF2A2418),
        favoriteCardEnd = Color(0xFF14161F),
        favoritePatternTint = Color(0xFFFBBF24),
        favoriteCardBorder = Color(0x59FBBF24),
        brandTileStart = Color(0xFF60A5FA),
        brandTileEnd = Color(0xFF2563EB),
        nextPrayerSurface = Color(0xFF1E3055),
        patternTint = Color(0xFF93C5FD),
        artBadgeSurface = Color(0xA60F172A),
        artBadgeInk = Color(0xFFF8FAFC),
        artPillSurface = Color(0xCC121B2E),
        artPillInk = Color(0xFFE8EDF7),
        artPatternTint = Color(0x1FFFFFFF),
        isDark = true,
    )
