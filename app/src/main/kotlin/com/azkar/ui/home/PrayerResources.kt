package com.azkar.ui.home

import com.azkar.generated.resources.Res
import com.azkar.generated.resources.asr
import com.azkar.generated.resources.asrTime
import com.azkar.generated.resources.asrValue
import com.azkar.generated.resources.dhuhr
import com.azkar.generated.resources.dhuhrTime
import com.azkar.generated.resources.dhuhrValue
import com.azkar.generated.resources.fajr
import com.azkar.generated.resources.fajrTime
import com.azkar.generated.resources.fajrValue
import com.azkar.generated.resources.isha
import com.azkar.generated.resources.ishaTime
import com.azkar.generated.resources.ishaValue
import com.azkar.generated.resources.maghrib
import com.azkar.generated.resources.maghribTime
import com.azkar.generated.resources.maghribValue
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

// Maps [PrayerKey] onto the generated string resources.
// // The old `Strings` built these keys by concatenation — `prayerTime` looked up `"${key}Time"` and
// `prayerValue` looked up `"${key}Value"` — so a typo in a key name was a runtime
// `MissingResourceException` and a renamed key silently rendered the wrong string. Naming all three
// explicitly here makes every one of the fifteen resources a compile-time reference instead.
// // As with the library enums, the enum itself stays resource-free so `PrayerTimesState` and the
// snapshot builder can be exercised without a resource environment.

/** The prayer's own name, e.g. "Fajr". */
val PrayerKey.nameResource: StringResource
    get() =
        when (this) {
            PrayerKey.FAJR -> Res.string.fajr
            PrayerKey.DHUHR -> Res.string.dhuhr
            PrayerKey.ASR -> Res.string.asr
            PrayerKey.MAGHRIB -> Res.string.maghrib
            PrayerKey.ISHA -> Res.string.isha
        }

/** The prayer's formatted time, e.g. "04:50 AM" — the long form shown in the table. */
val PrayerKey.timeResource: StringResource
    get() =
        when (this) {
            PrayerKey.FAJR -> Res.string.fajrTime
            PrayerKey.DHUHR -> Res.string.dhuhrTime
            PrayerKey.ASR -> Res.string.asrTime
            PrayerKey.MAGHRIB -> Res.string.maghribTime
            PrayerKey.ISHA -> Res.string.ishaTime
        }

/** The prayer's raw time value, e.g. "04:50" — the short form parsed into a time. */
val PrayerKey.valueResource: StringResource
    get() =
        when (this) {
            PrayerKey.FAJR -> Res.string.fajrValue
            PrayerKey.DHUHR -> Res.string.dhuhrValue
            PrayerKey.ASR -> Res.string.asrValue
            PrayerKey.MAGHRIB -> Res.string.maghribValue
            PrayerKey.ISHA -> Res.string.ishaValue
        }

/** Resolves [nameResource] for callers that build the text outside a composition. */
suspend fun PrayerKey.name(): String = getString(nameResource)

/** Resolves [timeResource] for callers that build the text outside a composition. */
suspend fun PrayerKey.displayTime(): String = getString(timeResource).trim()

/** Resolves [valueResource] for callers that build the text outside a composition. */
suspend fun PrayerKey.value(): String = getString(valueResource).trim()
