package com.azkar.ui.theme

import androidx.compose.runtime.Immutable

/**
 * The reader's two typographic choices: which face the app draws in, and how large it draws.
 *
 * Both were fixed constants until now — Inter for Latin, Amiri for Arabic, and a type ramp cut once
 * and never revisited — and neither was a decision the app was wrong to have made on the reader's
 * behalf. A reader who needs 18sp body text to read at all cannot be told that the app's design
 * system prefers 16sp; a reader whose system already carries a face they have chosen to read in all
 * day is being made to learn a second one for the eight seconds a reminder is up.
 *
 * ## Why the scale is a percentage of the density rather than a rebuilt ramp
 *
 * [scale] is applied by multiplying the font scale of the tree's `LocalDensity`, not by re-deriving
 * [AzkarTypeScale] and not by touching the `Typography` object. Two reasons, and both of them are
 * about the app's own size rather than about elegance:
 *
 * - The ramp is not what most of this app's text is drawn with. Screens reach for `MaterialTheme.typography`
 *   in the places that were migrated to Material and spell `14.sp` everywhere else. Scaling the ramp
 *   would move the first and leave the second, which is a setting that looks broken on half the screens.
 * - `sp` is *defined* as a density-scaled unit, so a multiplier on `fontScale` is the one lever that
 *   means the same thing to a hardcoded size, a Material component, and a text field's own text.
 *
 * Layout does not move with it, and that is deliberate. A larger font with the same paddings clips
 * labels rather than reflowing them, which is why [AzkarAppState]'s size control is worth pairing
 * with the card sizing that already exists.
 *
 * ## Why the face is a name and not an index
 *
 * The list of installed families is a property of the machine, not of the app: it is different on
 * every desktop, changes when software is installed, and the same stored name may not exist on the
 * machine the settings file is next copied to. So the stored value is the family *name* and every
 * lookup resolves it against what this machine actually has — see [SystemFontCatalogue]. The cost is
 * that a name can go stale, which is why [isAzkarFont] rather than a null check is what the theme
 * asks.
 */
@Immutable
data class AppTypography(
    /** A system family name, or [AZKAR_FONT] for the bundled Inter/Amiri pairing. */
    val family: String = AZKAR_FONT,
    /** Text size as a percentage of the ramp, for display and for storage. */
    val scalePercent: Int = DEFAULT_SCALE_PERCENT,
) {
    /** The same choice as a multiplier the density can take. */
    val scale: Float get() = scalePercent / PERCENT

    /**
     * Whether the reader is on the faces the app ships.
     *
     * Asked as a property rather than by comparing [family] to [AZKAR_FONT] at the call site because
     * the question is "may I use the bundled pairing", and there is exactly one answer to that
     * question no matter what the stored string looks like.
     */
    val isAzkarFont: Boolean get() = family == AZKAR_FONT

    /**
     * The nearest value this app can actually draw, for a pair read back off disk.
     *
     * A stored scale is not validated the way the card's dimensions are. The dimensions have to be,
     * because they are laid out in fixed pixels and a wild value puts a card outside its own window;
     * a scale is a multiplier on a ramp that is already bounded, and a clamp is both survivable and
     * the more useful answer — a settings file carried between machines with different display
     * settings should arrive usable, not refused.
     */
    fun coerced(): AppTypography =
        if (scalePercent in MIN_SCALE_PERCENT..MAX_SCALE_PERCENT) {
            this
        } else {
            copy(scalePercent = scalePercent.coerceIn(MIN_SCALE_PERCENT, MAX_SCALE_PERCENT))
        }

    companion object {
        /**
         * The sentinel for "use the faces the app ships".
         *
         * An empty string rather than a font name, so that the default is a value that cannot collide
         * with a family a machine happens to have: no desktop installs a font called `""`, and a
         * settings file that somehow lost the family still reads as the default rather than as a
         * missing glyph.
         */
        const val AZKAR_FONT = ""

        const val DEFAULT_SCALE_PERCENT = 100

        /**
         * The floor, which is below 1: this app is a reader of long Arabic passages, and a reader who
         * wants them smaller than the designer drew them is making a reasonable request rather than a
         * strange one. Matching Android's own 0.85 keeps the scale meaning the same thing as it does
         * everywhere else on the platform.
         */
        const val MIN_SCALE_PERCENT = 25

        /**
         * The ceiling, which is deliberately short of Android's 2.0.
         *
         * Past this the app's own layout stops coping: the nav rail holds a fixed width, the settings
         * rows hold a label and a control side by side, and a 200% font turns both into ellipses. The
         * setting is meant to fix legibility, not to prove a point — a reader who needs more than this
         * is better served by the operating system's own scaling, which this multiplies rather than
         * replaces.
         */
        const val MAX_SCALE_PERCENT = 200

        /** The steps the size control offers, so the slider and the stored value share a grid. */
        val SCALE_STEPS: List<Int> = (MIN_SCALE_PERCENT..MAX_SCALE_PERCENT step SCALE_STEP_PERCENT).toList()

        private const val SCALE_STEP_PERCENT = 25

        private const val PERCENT = 100f
    }
}
