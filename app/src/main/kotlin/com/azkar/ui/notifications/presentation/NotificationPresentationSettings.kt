package com.azkar.ui.notifications.presentation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azkar.i18n.localeOrNull
import com.azkar.ui.notifications.NotificationContent
import com.azkar.ui.notifications.NotificationNotice
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Which corner of the screen the overlay stacks against.
 *
 * Named for corners rather than for alignment because the overlay is a one-dimensional stack: an
 * alignment would also allow a centred or middle-edge position, and neither of those is something
 * the notification ever wants to be. Four names, no other states.
 */
@Serializable
enum class OverlayPlacement {
    TOP_START,
    TOP_END,
    BOTTOM_START,
    BOTTOM_END,
}

/**
 * How big one card is, offered as three whole answers before the sliders.
 *
 * A pair of numbers is the honest interface for a size — width and height are independent, and
 * "taller" without "narrower" is a real choice — but it is a poor *first* one, because a reader who
 * has not seen the card at four hundred by two hundred cannot tell whether they want one at three
 * hundred and a half by one-fifty, or why the height range starts where it does. The presets are the
 * starting point; the sliders are there for the person who wants something between them, and a
 * matching pair of sliders makes every one of these entries reachable by hand.
 *
 * Ordered smallest to largest so the group can be read as a scale.
 *
 * Both numbers of every entry sit exactly on a stop of the settings sliders — each is a whole number of
 * slider steps above the range's minimum — so choosing a preset lands the thumb on a bar rather than
 * between two of them. That is why the heights are the round hundreds-and-fifties they are and not the
 * 152/196/248 the first draft of the card was drawn against: a thumb parked between two bars is read as
 * a size the presets do not describe, which is exactly the impression the presets exist to prevent.
 */
enum class OverlayCardSize(
    val widthDp: Int,
    val heightDp: Int,
) {
    /**
     * The narrowest card that still holds a passage on two lines.
     *
     * Sized to the smallest thing worth printing rather than to the smallest thing that *fits*:
     * below this the header's counter and the cross start crowding each other, and a reminder that
     * has to be scrolled is not a glance.
     */
    COMPACT(320, 150),

    /** What the overlay shipped with, and the pair the first draft of the card was drawn against. */
    COMFORTABLE(400, 200),

    /** Room for four lines of passage, for passages that are longer than they are quotable. */
    ROOMY(480, 250),
}

/**
 * Whether a reminder card is drawn at the size the reader chose or at the size its passage needs.
 *
 * A fixed slot was the original design, and it buys one real thing: a stack whose cards never change
 * height as they arrive, so the card being read does not jump when the one below it expires. What it
 * costs is that a two-word dhikr and a paragraph are given the same box, and the reader pays for the
 * taller of the two on every reminder that is not it.
 *
 * [FIT] hands the height back to the passage and keeps the width the reader chose, which is the pair
 * that reads best: the width is the measure the text wraps to, and a measure is genuinely a matter of
 * taste, while the height is an artefact of how long the passage happens to be. [FIXED] is kept
 * because the jumping is real and some readers would rather have the slot than the fit — and because
 * a height that only ever appears when a particular passage is showing is hard to reason about.
 *
 * The chosen height is not discarded in [FIT] mode. It is the fallback: the size a card is given in the
 * one place its content cannot be measured (see [NotificationOverlayWindow], which measures the stack
 * it is about to draw), and the height the settings preview falls back to before the passage is laid
 * out. Nothing is stored that nothing can read.
 */
@Serializable
enum class OverlayCardSizing {
    /** Height follows the passage, up to its line ceiling; width is still the chosen width. */
    FIT,

    /** Every card is exactly the chosen width by the chosen height, whatever it holds. */
    FIXED,
}

/**
 * Whether a card is on screen for the time the reader chose or for the time reading it takes.
 *
 * [READING] is the answer to "how long is a reminder" when it is asked of cards rather than of the
 * reader: the words on it are reachable at reading speed, not in a fixed number the reader has to
 * guess. What the fixed number is then for is the same thing the chosen height is for under a fitted
 * card — [FIXED] keeps the turn-the-number behaviour, and [READING] falls back to nothing but its own
 * bounds, because the bounds are shared with [durationSeconds] and a card that earns a minute is
 * allowed one whether the reader set four seconds or forty.
 */
@Serializable
enum class OverlayDurationMode {
    /** How long the card stays is how long its text takes to read. */
    READING,

    /** Every card is on screen for the same chosen number of seconds, whatever it holds. */
    FIXED,
}

/**
 * How a fired reminder reaches the user, as opposed to *when* it should.
 *
 * Deliberately separate from [com.azkar.ui.notifications.ReminderQueueConfig]: the queue decides
 * that something is due and this decides only how it is shown. Nothing here can change whether a
 * reminder fires, which is what makes the overlay safe to add without touching the scheduler, and
 * what lets a user turn the overlay's cosmetics around without disturbing which passages they
 * actually receive.
 *
 * Every field here is optional in the sense that there is no "overlay off" switch, because the user
 * asked for one consistent notification appearance everywhere — including while the main window is
 * in front — and a windowed Compose card is the only thing that can be that. The operating system's
 * own notification channel was the one thing this settings model used to offer alongside it, and
 * was removed: the same announcement drawn by the desktop in the desktop's own styling, with a
 * lifetime the app does not own, is the opposite of consistent, and there is no honest setting that
 * makes it one.
 */
@Serializable
data class NotificationPresentationSettings(
    /**
     * The fixed value [OverlayDurationMode.FIXED] shows a card for, in seconds; the store bounds and
     * the [OverlayDurationMode.READING] ceiling come from the same pair.
     */
    val durationSeconds: Int = 8,
    /**
     * Whether a card's time on screen is chosen or computed, defaulting to computed.
     *
     * The default follows [cardSizing]'s, for the same reason: a number the reader had to pick
     * answered "how long", and the honest answer differs between a two-word dhikr and a paragraph.
     * Timing a card to its passage is the fit sibling of fitting it to its size — the shortest honest
     * answer for both is "as long as the text is".
     */
    val durationMode: OverlayDurationMode = OverlayDurationMode.READING,
    val placement: OverlayPlacement = OverlayPlacement.TOP_END,
    /** How many cards the stack holds at once; a newer card evicts the oldest beyond this. */
    val maxVisible: Int = 3,
    /**
     * One card's width, in dp, from the edge of the stack's column.
     *
     * Stored as a plain integer rather than as a [Dp] so the value stays a serialisable number that
     * survives a round trip through DataStore untouched; [cardWidth] is the typed accessor for
     * everything that lays a card out.
     */
    val cardWidthDp: Int = OverlayCardSize.COMFORTABLE.widthDp,
    /**
     * One card's height, in dp — the height it is drawn at under [OverlayCardSizing.FIXED], and the
     * height a card falls back to wherever its content cannot be measured under
     * [OverlayCardSizing.FIT].
     *
     * Stored for the same reason as [cardWidthDp], and still meaningful in both modes: the fit mode
     * has to start somewhere, and "the last height the reader actually chose" is a better somewhere
     * than a constant written here.
     */
    val cardHeightDp: Int = OverlayCardSize.COMFORTABLE.heightDp,
    /**
     * Whether a card is the chosen size or the size its passage needs, defaulting to the passage.
     *
     * The default is [OverlayCardSizing.FIT] because the fixed slot was a default nobody asked for:
     * the size settings were introduced to answer "how big is a reminder", and the honest answer for
     * a passage that is three words long is not the same as for one that is three lines long. A reader
     * who wants the steadier fixed slot still has it, one tap away, and their chosen height is kept as
     * the fallback either way.
     */
    val cardSizing: OverlayCardSizing = OverlayCardSizing.FIT,
    /**
     * The language the reminder *text* is written in, as a stored language tag, or blank.
     *
     * Blank means "the app's own language", and it is the default, so an existing reader sees no change
     * and a reader who has never touched this setting keeps being reminded in whatever language they
     * already read the app in. Only the reminded passage is affected: the app's own chrome around it —
     * the window, the settings, the word that says a reminder has arrived — stays in the interface
     * language, because a reminder written in one language inside an interface written in another is what
     * this setting is *for*, not a fault to be smoothed away.
     *
     * A language tag rather than an enum because the app's languages are described by
     * [com.azkar.i18n.supportedLocales] and this must not be a second, silently divergent list of them;
     * [notificationLocale] is the typed accessor and the only place the tag is parsed.
     */
    val notificationLanguageTag: String = "",
) {
    init {
        require(durationSeconds in MIN_DURATION_SECONDS..MAX_DURATION_SECONDS)
        require(maxVisible in MIN_VISIBLE..MAX_VISIBLE)
        require(cardWidthDp in MIN_CARD_WIDTH_DP..MAX_CARD_WIDTH_DP)
        require(cardHeightDp in MIN_CARD_HEIGHT_DP..MAX_CARD_HEIGHT_DP)
    }

    val durationMillis: Long get() = durationSeconds * 1_000L

    /**
     * Whether a card's time on screen is the passage's rather than [durationSeconds]'s.
     *
     * The fitted-height accessor's twin, for the same reason as [usesFittedHeight]: the overlay, the
     * settings and the model itself all have to ask it, and a named question is one that cannot be
     * asked inverted.
     */
    val usesReadingTime: Boolean get() = durationMode == OverlayDurationMode.READING

    /**
     * How long [notice] earns on screen, in milliseconds.
     *
     * The one decision the expiry machinery depends on, so it lives here rather than in the
     * presenter's `appendLocked`: there it would read as "compute a number and store it", and the
     * reading model behind it would be invisible to the test that pins it. Given a whole notice
     * rather than a bare string so the text it measures — the body the card actually draws — is
     * chosen in the same place the time is computed from it.
     */
    fun durationFor(notice: NotificationNotice): Long =
        if (usesReadingTime) {
            readingMillisFor(text = notice.textToRead())
        } else {
            durationMillis
        }

    val cardWidth: Dp get() = cardWidthDp.dp

    val cardHeight: Dp get() = cardHeightDp.dp

    /**
     * Whether the card's own height is the passage's rather than [cardHeight]'s.
     *
     * A named question rather than comparing the enum at each call site: three places — the overlay,
     * the card and the settings preview — all have to ask it, and three `== OverlayCardSizing.FIT`
     * comparisons are three chances for one of them to be inverted.
     */
    val usesFittedHeight: Boolean get() = cardSizing == OverlayCardSizing.FIT

    /** The preset a stored pair sits on, or null when the sliders have been moved off all three. */
    val cardSizePreset: OverlayCardSize?
        get() = OverlayCardSize.entries.firstOrNull { it.widthDp == cardWidthDp && it.heightDp == cardHeightDp }

    /**
     * The three numbers the overlay window's own size is derived from, as one key.
     *
     * [NotificationOverlayWindow] resizes on a change to this rather than on a change to any one
     * field, and joining them here means a window whose size depended on three settings cannot end
     * up keyed on one of them — which is the bug that leaves a card 480 wide in a 400-wide window
     * until the next restart.
     *
     * [cardSizing] is deliberately not a fourth number here. It does not describe the window's size; it
     * decides whether the window's size is even derived from these three, and the overlay keys the mode
     * alongside this rather than inside it so the two questions stay separable.
     */
    val cardSizeKey: Triple<Int, Int, Int> get() = Triple(cardWidthDp, cardHeightDp, maxVisible)

    /**
     * The chosen reminder language, or `null` to mean "the language the app is in".
     *
     * Parsing goes through [localeOrNull], the app's one rule for a stored language tag, so a tag this
     * version cannot read reads as "follow the app" rather than as a language nobody chose — and the
     * rule for what counts as unusable does not have to be restated here to be kept in step with the
     * interface language, which stores its own tag the same way.
     */
    val notificationLocale: Locale? get() = notificationLanguageTag.localeOrNull()

    /**
     * Clamps a decoded or edited value back into range instead of throwing on it.
     *
     * A stored language tag is normalised the same way: an unusable one becomes blank — "follow the
     * app" — so a bad tag heals on the next write instead of being carried forward. It is not a
     * `require` because a `require` would fail the whole blob's decode over one unreadable field and
     * reset every unrelated setting with it.
     */
    fun validated(): NotificationPresentationSettings =
        copy(
            durationSeconds = durationSeconds.coerceIn(MIN_DURATION_SECONDS, MAX_DURATION_SECONDS),
            maxVisible = maxVisible.coerceIn(MIN_VISIBLE, MAX_VISIBLE),
            cardWidthDp = cardWidthDp.coerceIn(MIN_CARD_WIDTH_DP, MAX_CARD_WIDTH_DP),
            cardHeightDp = cardHeightDp.coerceIn(MIN_CARD_HEIGHT_DP, MAX_CARD_HEIGHT_DP),
            notificationLanguageTag =
                if (notificationLanguageTag.localeOrNull() ==
                    null
                ) {
                    ""
                } else {
                    notificationLanguageTag
                },
        )
}

// File-private rather than a `private companion object` on a `@Serializable` class: the generated
// serializer reaches `Companion` from outside the class, and a private one fails at runtime with an
// `IllegalAccessError` the first time anybody encodes or decodes it. The bounds are the same either
// way — both names are invisible past this file.
//
// The card's bounds are the widest and tallest of the presets plus the room the sliders need on each
// side, so every preset is inside the range and every slider position is a legal stored value. The
// width ceiling is set by screen real estate rather than by taste: past roughly five hundred the card
// is wide enough that a two-line passage wraps into one line of very long measure, which is harder to
// read than three short ones.
private const val MIN_DURATION_SECONDS = 3
private const val MAX_DURATION_SECONDS = 120
private const val MIN_VISIBLE = 1
private const val MAX_VISIBLE = 5
private const val MIN_CARD_WIDTH_DP = 300
private const val MAX_CARD_WIDTH_DP = 700
private const val MIN_CARD_HEIGHT_DP = 120
private const val MAX_CARD_HEIGHT_DP = 400

/*
 * The reading-time model behind [OverlayDurationMode.READING]: the time it takes to *see* a card is
 * not the same quantity as the time it takes to read it, so the two are added rather than merged.
 *
 * The base is the perceptual cost — the moment of noticing something appeared, finding where to look,
 * reading the little header — which is charged even on a card whose body is a single word; ~200
 * words a minute is the commonly quoted average reading rate for someone read silently with intent,
 * which is what a reminder the reader chose to receive is. Once a card passes ~30 seconds the
 * reading rate stops being the honest description anyway, so the ceiling is the store's own
 * durationSeconds ceiling rather than the rate unrolled — a long passage reaching the cap is a card
 * the reader would have dismissed themselves long before.
 */
private const val READING_BASE_MILLIS = 2_500L

private const val READING_WORDS_PER_MINUTE = 200L

private const val READING_MILLIS_PER_WORD = 60_000L / READING_WORDS_PER_MINUTE

/** Where the text behind [OverlayDurationMode.READING] is chunked into words. */
private val READING_WORD_BREAK = Regex("\\s+")

/**
 * The text on a notice that earns it time, as the card body shows it: a passage's words are what a
 * reader reads, a prayer's label is the only sentence-like thing on it, and a plain notice's message
 * is its body — its title is the card's caption, closer to a word than to a sentence.
 */
private fun NotificationNotice.textToRead(): String =
    when (val shown = content) {
        is NotificationContent.Remembrance -> shown.text
        is NotificationContent.Prayer -> shown.prayer
        null -> message
    }

/**
 * Reading time for [text], in milliseconds, at the model's rate and clamped to the store's bounds.
 *
 * The clamp shares [MIN_DURATION_SECONDS] and [MAX_DURATION_SECONDS] rather than restating them, so
 * the mode cannot produce a number the store would have refused in [FIXED] — the two modes differ
 * about where a duration comes from, not about what a duration is.
 */
private fun readingMillisFor(text: String): Long {
    val words = text.split(READING_WORD_BREAK).count { it.isNotBlank() }
    return (READING_BASE_MILLIS + words * READING_MILLIS_PER_WORD)
        .coerceIn(MIN_DURATION_SECONDS * 1_000L, MAX_DURATION_SECONDS * 1_000L)
}
