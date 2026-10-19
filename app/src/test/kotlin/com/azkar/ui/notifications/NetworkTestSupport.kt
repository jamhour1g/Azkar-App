package com.azkar.ui.notifications

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The two pieces every network test in this package builds the same way.
 *
 * A scripted engine and an [HttpClient] over it, with the same content negotiation the app installs,
 * so a client under test parses its provider's bytes through the real stack instead of a hand-rolled
 * decode. Shared rather than copied because the geocoder and the time-zone resolver are tested
 * against the same three shapes — a success, a provider error, and a body that is missing the field
 * the caller wanted — and two copies of that setup is two places for the JSON configuration to drift
 * away from the app's.
 */
internal fun testHttpClient(engine: MockEngine): HttpClient =
    HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

/** A [MockEngine] that answers every request with [body] and [status] as JSON. */
internal fun jsonEngine(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): MockEngine =
    MockEngine {
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
