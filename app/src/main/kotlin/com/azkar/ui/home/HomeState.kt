package com.azkar.ui.home

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.azkar.domain.model.Remembrance
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.favoriteAzkarArabic
import com.azkar.generated.resources.favoriteAzkarEnglish
import com.azkar.generated.resources.favoriteAzkarSource
import com.azkar.i18n.RemembranceI18n
import com.azkar.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import java.util.Locale
import kotlin.random.Random

/**
 * The three texts rendered by `FavoritesCardComponent`, its navigation availability, and where the
 * visible one sits in the carousel.
 *
 * The `arabic`/`english` names are the JavaFX FXML node ids and describe the *slot*, not the script:
 * in the Arabic locale `arabic` holds the Arabic dhikr and `english` its explanation, and in the
 * English locale both fields hold English. See [RemembranceI18n].
 *
 * [position] is 1-based so the card can read "3 / 12" without the caller doing arithmetic; both
 * counters are 0 when the carousel is empty, which is also the only state in which they are not
 * worth rendering. The JavaFX card had no counter at all, so there was nothing to contradict here —
 * but the arrows moved through a list silently, and a user could not tell a single favourite from
 * the fifth of nine.
 */
@Immutable
data class FavoriteState(
    val arabic: String,
    val english: String,
    val source: String,
    val navigationEnabled: Boolean,
    val position: Int,
    val total: Int,
)

/**
 * Port of `com.azkar.components.home.controller.FavoritesController`.
 *
 * `loadFavorites` blocked on a JPA read; here [loadFavorites] only stores the list and the
 * screen recomputes the visible text from [currentFavorite], so the same
 * `RemembranceI18n.resolveTexts` fallback chain is preserved without a second write path.
 */
@Stable
class FavoritesState(
    private val uiLocale: Locale,
) {
    private var favorites: List<Remembrance> = emptyList()
    private var index: Int = -1

    /**
     * The three placeholder texts, resolved once per locale by [loadPlaceholders].
     *
     * They are empty until then, so the first composition renders an empty card rather than
     * placeholder text in the wrong language. [loadFavorites] and the [step] handlers are not all
     * suspend — they are click handlers — so the strings have to be fields rather than an await on
     * every read.
     */
    private var placeholderArabic: String = ""
    private var placeholderEnglish: String = ""
    private var placeholderSource: String = ""

    var state: FavoriteState by mutableStateOf(EMPTY_FAVORITE)
        private set

    /** Resolves the placeholders, which the sync paths below then read as plain fields. */
    suspend fun loadPlaceholders() {
        placeholderArabic = getString(Res.string.favoriteAzkarArabic)
        placeholderEnglish = getString(Res.string.favoriteAzkarEnglish)
        placeholderSource = getString(Res.string.favoriteAzkarSource)
    }

    fun loadFavorites(loaded: List<Remembrance>) {
        if (loaded.isEmpty()) {
            applyFallback()
            return
        }
        favorites = loaded
        index = Math.floorMod(Random.nextInt(loaded.size), loaded.size)
        state = resolve(loaded[index])
    }

    fun showPrevious() = step(-1)

    fun showNext() = step(1)

    private fun step(delta: Int) {
        if (favorites.isEmpty()) return
        index = Math.floorMod(index + delta, favorites.size)
        state = resolve(favorites[index])
    }

    fun applyFallback() {
        favorites = emptyList()
        index = -1
        state = fallback()
    }

    /**
     * The placeholder shown when nothing has been loaded, or nothing could be.
     *
     * The counters are 0 rather than 1 of 0: there is no position in an empty carousel, and the card
     * reads that as "no favourites yet" instead of printing a fraction.
     */
    private fun fallback() =
        FavoriteState(
            arabic = placeholderArabic,
            english = placeholderEnglish,
            source = placeholderSource,
            navigationEnabled = false,
            position = 0,
            total = 0,
        )

    private fun resolve(remembrance: Remembrance): FavoriteState {
        val texts =
            RemembranceI18n.resolveTexts(
                remembrance,
                uiLocale,
                placeholderArabic,
                placeholderEnglish,
            )
        return FavoriteState(
            arabic = texts.translation,
            english = texts.explanation,
            source = remembrance.source ?: placeholderSource,
            navigationEnabled = favorites.size > 1,
            position = index + 1,
            total = favorites.size,
        )
    }

    private companion object {
        val EMPTY_FAVORITE =
            FavoriteState(
                arabic = "",
                english = "",
                source = "",
                navigationEnabled = false,
                position = 0,
                total = 0,
            )
    }
}

/**
 * The per-prayer notification switches, driven by the settings store.
 *
 * Port of `com.azkar.components.home.controller.NotificationSettingsController`, which mirrored the
 * switches between `java.util.prefs` and its own mutable fields. The store owns the stored value now
 * and this class observes it, so a switch cannot drift out of step with what is on disk — the
 * failure that mirroring had, where a local shadowed the backing store, left it empty, and one bell
 * could turn the master switch off while the others stayed on.
 *
 * The language is the opposite case: it is written optimistically rather than observed, because
 * [com.azkar.i18n.LocaleStore.persist] runs during a click and the tree has to repaint immediately. The
 * switches only ever reach the UI through Compose, so the store's round trip is quick enough to
 * await and there is no visible lag — keeping one source of truth costs nothing here.
 *
 * The master switch is not stored. It is the summary of [flags] — on while at least one bell is on
 * — which is what makes it impossible for the two to disagree.
 *
 * [store] is required rather than defaulted. The default used to be `SettingsStore.instance()`, which
 * meant a caller that forgot to pass one subscribed to the developer's own preferences; now the
 * screen asks the container for the store and a test names the file it wants to watch.
 */
@Stable
class NotificationSettingsState(
    private val store: SettingsStore,
    private val scope: CoroutineScope,
) {
    /**
     * The stored switch for each prayer; [PrayerTimesState] applies these to the rendered rows.
     *
     * Starts on, which is also what a store with nothing in it resolves to, so there is no flash of
     * off-buttons while the first read is in flight.
     */
    var flags: Map<PrayerKey, Boolean> by mutableStateOf(PrayerKey.entries.associateWith { true })
        private set

    /** The master switch: a summary of [flags], so it always agrees with the rows beneath it. */
    val globalEnabled: Boolean get() = flags.values.any { it }

    /** Collects stored switches from a composition-owned effect, never from construction. */
    suspend fun observe() {
        store.changes.collect { stored ->
            flags = PrayerKey.entries.associateWith { stored[flagKeyFor(it)] ?: true }
        }
    }

    /**
     * Mirrors the master switch onto every row.
     *
     * All five keys go in one [SettingsStore.edit] so the change is committed as a single
     * transaction — an observer must never see the master switch applied to only some of its rows.
     */
    fun onGlobalToggleChanged(enabled: Boolean) {
        scope.launch {
            store.edit {
                PrayerKey.entries.forEach { key -> this[flagKeyFor(key)] = enabled }
            }
        }
    }

    /** Mirrors a single row switch; the master switch follows from [flags] on the next emission. */
    fun onPrayerToggled(
        key: PrayerKey,
        enabled: Boolean,
    ) {
        scope.launch { store.edit { this[flagKeyFor(key)] = enabled } }
    }

    private fun flagKeyFor(key: PrayerKey): Preferences.Key<Boolean> = booleanPreferencesKey("prayer.${key.id}.enabled")
}
