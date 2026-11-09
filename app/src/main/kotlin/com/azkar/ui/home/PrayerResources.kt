package com.azkar.ui.home

import com.azkar.generated.resources.Res
import com.azkar.generated.resources.asr
import com.azkar.generated.resources.asrValue
import com.azkar.generated.resources.dhuhr
import com.azkar.generated.resources.dhuhrValue
import com.azkar.generated.resources.fajr
import com.azkar.generated.resources.fajrValue
import com.azkar.generated.resources.isha
import com.azkar.generated.resources.ishaValue
import com.azkar.generated.resources.maghrib
import com.azkar.generated.resources.maghribValue
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

// Keep resource names explicit so missing or renamed keys are caught at compile time.

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

/** Resolves [valueResource] for callers that build the text outside a composition. */
suspend fun PrayerKey.value(): String = getString(valueResource).trim()
