package com.azkar.ui.notifications

import androidx.compose.runtime.Immutable
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/**
 * A place the location picker can hand to the prayer calculator.
 *
 * The picker's search results are the only source of these, and each carries the coordinates and the
 * IANA time zone the provider resolved, so a picked place is a complete answer with no second lookup.
 */
@Immutable
data class PrayerPlace(
    val name: String,
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
    val zoneId: String?,
) {
    /** What the suggestion list shows: the city, then whatever narrows it down, then the country. */
    val label: String
        get() =
            listOfNotNull(name, region?.takeIf { it.isNotBlank() && it != name }, country)
                .joinToString(", ")
}

/**
 * Place search for the location picker.
 *
 * The provider is Open-Meteo's geocoder, which is the rare free endpoint that returns an IANA time
 * zone beside the coordinates. It needs no API key, which is the same constraint the app's IP lookup
 * already lives under.
 *
 * The endpoint is best-effort and can fail: the caller wraps every call in `runCatching` and shows a
 * notice, exactly as it does for [LocationDetector]. This class does not swallow failures into empty
 * results, because "the network is down" and "no city is called Qqq" deserve different messages.
 */
class GeocodingClient(
    private val client: HttpClient,
) {
    /**
     * Cities and places matching [query], most relevant first.
     *
     * [language] is the language the result names come back in — the app's locale, so an Arabic
     * interface finds "القاهرة" as readily as "Cairo". Open-Meteo omits the `results` key entirely
     * when nothing matches rather than returning an empty array, which the DTO's default covers.
     */
    suspend fun searchPlaces(
        query: String,
        language: String,
    ): List<PrayerPlace> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val response: HttpResponse =
            client.get("https://geocoding-api.open-meteo.com/v1/search") {
                parameter("name", trimmed)
                parameter("count", SEARCH_LIMIT)
                parameter("language", language)
                parameter("format", "json")
            }
        require(response.status.isSuccess()) { "Place search failed (${response.status.value})" }
        return response.body<GeocodingResponse>().results.map { result ->
            PrayerPlace(
                name = result.name,
                region = result.admin1,
                country = result.country,
                latitude = result.latitude,
                longitude = result.longitude,
                zoneId = result.timezone?.takeIf { it.isNotBlank() },
            )
        }
    }

    @Serializable
    private data class GeocodingResponse(
        val results: List<GeocodingResult> = emptyList(),
    )

    @Serializable
    private data class GeocodingResult(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val timezone: String? = null,
        val country: String? = null,
        val admin1: String? = null,
    )

    private companion object {
        /** Enough suggestions to cover a common name without turning the dropdown into a page. */
        const val SEARCH_LIMIT = 8
    }
}
