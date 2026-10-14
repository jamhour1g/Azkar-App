package com.azkar.ui.notifications

import com.azkar.ui.home.PrayerKey
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** Offline solar-angle prayer time calculation. Times are returned in the configured local zone. */
object PrayerTimeCalculator {
    fun calculate(
        date: LocalDate,
        location: PrayerLocationSettings,
    ): Map<PrayerKey, LocalTime> {
        val zone = ZoneId.of(location.zoneId)
        val day = date.dayOfYear.toDouble()
        val gamma = 2.0 * Math.PI / 365.0 * (day - 1.0)
        val equationOfTime =
            229.18 * (
                0.000075 +
                    0.001868 * cos(gamma) -
                    0.032077 * sin(gamma) -
                    0.014615 * cos(2.0 * gamma) -
                    0.040849 * sin(2.0 * gamma)
            )
        val declination =
            0.006918 -
                0.399912 * cos(gamma) +
                0.070257 * sin(gamma) -
                0.006758 * cos(2.0 * gamma) +
                0.000907 * sin(2.0 * gamma) -
                0.002697 * cos(3.0 * gamma) +
                0.00148 * sin(3.0 * gamma)

        val latitude = Math.toRadians(location.latitude)
        val longitude = location.longitude
        val middayZoneOffset =
            date
                .atTime(12, 0)
                .atZone(zone)
                .offset.totalSeconds / 3600.0
        val solarNoonMinutes = 720.0 - (4.0 * longitude) - equationOfTime + (60.0 * middayZoneOffset)

        fun hourAngle(zenithDegrees: Double): Double {
            val zenith = Math.toRadians(zenithDegrees)
            val cosine =
                (cos(zenith) - sin(latitude) * sin(declination)) /
                    (cos(latitude) * cos(declination))
            return Math.toDegrees(acos(cosine.coerceIn(-1.0, 1.0)))
        }

        fun localTime(minutes: Double): LocalTime {
            val normalized = ((minutes % 1440.0) + 1440.0) % 1440.0
            val rounded = floor(normalized + 0.5).toInt() % 1440
            return LocalTime.of(rounded / 60, rounded % 60)
        }

        val fajr = solarNoonMinutes - 4.0 * hourAngle(90.0 + location.method.fajrAngle)
        val dhuhr = solarNoonMinutes + 2.0
        val asrShadowFactor = 1.0
        val asrAltitude =
            Math.toDegrees(
                atan(
                    1.0 /
                        (asrShadowFactor + tan(kotlin.math.abs(latitude - declination))),
                ),
            )
        val asr = solarNoonMinutes + 4.0 * hourAngle(90.0 - asrAltitude)
        val maghrib = solarNoonMinutes + 4.0 * hourAngle(90.833)
        val isha =
            if (location.method.fixedIshaMinutes != null) {
                maghrib + location.method.fixedIshaMinutes
            } else {
                solarNoonMinutes + 4.0 * hourAngle(90.0 + requireNotNull(location.method.ishaAngle))
            }

        return mapOf(
            PrayerKey.FAJR to localTime(fajr),
            PrayerKey.DHUHR to localTime(dhuhr),
            PrayerKey.ASR to localTime(asr),
            PrayerKey.MAGHRIB to localTime(maghrib),
            PrayerKey.ISHA to localTime(isha),
        )
    }
}
