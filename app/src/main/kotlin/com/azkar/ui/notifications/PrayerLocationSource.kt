package com.azkar.ui.notifications

/**
 * Where prayer-time coordinates come from.
 *
 * A seam rather than a class, because the scheduler must be able to ask "where am I?" without
 * knowing that the answer arrives over the network. The production answer is [LocationDetector]'s
 * best-effort IP lookup; the answer a test needs is a fixed place on the map, and a scheduler that
 * could only be asked over HTTP could not be tested at all without a network.
 */
interface PrayerLocationSource {
    /** The location to calculate prayer times from, or a failure if it cannot be determined. */
    suspend fun detect(): PrayerLocationSettings
}
