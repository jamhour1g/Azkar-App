package com.azkar.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import java.awt.GraphicsEnvironment
import java.util.concurrent.ConcurrentHashMap

/**
 * The faces installed on *this* machine, and the bridge from a family name to a Compose [FontFamily].
 *
 * ## Why the desktop's own fonts are on the table
 *
 * `app/src/main/resources/com/azkar/fonts` holds Inter and Amiri and nothing else. That was the
 * right call for a first version — bundling two faces is a licence to reason about and two files on
 * disk rather than a font picker that has to know something about every OS's font configuration — but
 * it is also the reason a reader who has set their whole desktop to a particular face cannot use it
 * here. Compose Desktop can load an installed family by name
 * (`FontFamily(familyName)`), so there is no reason to make them choose between "Azkar's type" and
 * "the type I read in all day".
 *
 * ## Why AWT does the asking
 *
 * Compose exposes no API for this. Its desktop text backend keeps a font collection built from the
 * platform, and there is no supported way to enumerate it, so the enumeration is done with AWT — the
 * same font configuration the backend itself is drawing from, which means a family this lists is a
 * family the backend can resolve. The alternative, shelling out to `fc-list` or reading the Windows
 * font registry, would ask the question twice in two different ways and disagree on the boundaries.
 *
 * Everything here degrades to "there are no system fonts" rather than throwing. A headless test run,
 * a CI container with no fontconfig, and a Linux box with only the four JDK logical fonts are all
 * ordinary places for this app to be started, and none of them is a reason to lose the app window.
 */
internal object SystemFontCatalogue {
    /**
     * Families the desktop has installed, sorted the way a font menu is.
     *
     * Cached because it costs a round trip into the platform's font configuration, which does not
     * change while the process is alive. A `by lazy` rather than a `var`, so a failed read is
     * remembered as a failure instead of being retried on every recomposition.
     */
    val installed: List<String> by lazy { readInstalled() }

    /**
     * Faces built on demand, one per name the reader has actually chosen.
     *
     * Not cached for its own sake — [FontFamily] registers eighteen faces with the text backend when
     * it is built, so building one per recomposition of the settings screen would be a stall on every
     * frame. The map is bounded by the number of families a reader cycles through, which is one in
     * practice.
     */
    private val resolved = ConcurrentHashMap<String, FontFamily>()

    /**
     * The Compose family for a name this machine has, or `null` when it does not.
     *
     * `null` rather than a fallback face because the caller has a better answer available: it can
     * keep using the bundled pairing, which is a real typeface, rather than accept whatever Compose
     * substitutes for an unknown name — which is a system font, which is the very thing that just
     * turned out to be missing.
     */
    @OptIn(ExperimentalTextApi::class)
    fun family(name: String): FontFamily? =
        if (name.isBlank() || installed.none { it.equals(name, ignoreCase = true) }) {
            null
        } else {
            resolved.getOrPut(name) { FontFamily(name) }
        }

    /**
     * Every family the desktop reports as installed, for the font picker.
     *
     * This is the full catalogue, not a curated subset: excluding a family because it is uncommon
     * makes a system-font picker fail precisely for the reader who installed that family. The shared
     * [androidx.compose.material3.DropdownMenu] supplies a bounded scrolling viewport for the longer
     * list, while the catalogue remains machine-specific and alphabetically ordered.
     */
    val choices: List<String> by lazy { installed }

    /**
     * Whether [name] names something on this machine.
     *
     * Separate from [family] because the settings screen needs to answer the question without building
     * eighteen faces: a stored family that no longer exists has to be *shown* as missing, and [family]
     * would have built one on the way to saying so.
     */
    fun knows(name: String): Boolean = installed.any { it.equals(name, ignoreCase = true) }

    private fun readInstalled(): List<String> =
        runCatching {
            if (GraphicsEnvironment.isHeadless()) return@runCatching emptyList()
            GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .availableFontFamilyNames
                .toList()
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }
        }.getOrDefault(emptyList())
}
