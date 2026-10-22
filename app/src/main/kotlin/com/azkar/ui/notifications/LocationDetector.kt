package com.azkar.ui.notifications

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Best-effort IP geolocation. Manual coordinates remain available when the network lookup fails. */
class LocationDetector(
    private val client: HttpClient,
) : PrayerLocationSource,
    AutoCloseable {
    override suspend fun detect(): PrayerLocationSettings {
        val response: HttpResponse =
            client.get("https://ipapi.co/json/") {
                header("User-Agent", "Azkar-Desktop")
            }
        require(response.status.isSuccess()) { "Location lookup failed (${response.status.value})" }
        val location = response.body<GeoLocationResponse>()
        val city =
            listOfNotNull(
                location.city,
                location.countryName,
            ).joinToString(", ").ifBlank { "Detected location" }
        val zoneId = location.timezone?.takeIf { it.isNotBlank() } ?: "UTC"
        return PrayerLocationSettings(
            latitude = location.latitude,
            longitude = location.longitude,
            city = city,
            zoneId = zoneId,
            method = PrayerCalculationMethod.MUSLIM_WORLD_LEAGUE,
            autoDetect = true,
        )
    }

    override fun close() {
        client.close()
    }

    @Serializable
    private data class GeoLocationResponse(
        val latitude: Double,
        val longitude: Double,
        val city: String? = null,
        @SerialName("country_name") val countryName: String? = null,
        val timezone: String? = null,
    )
}
