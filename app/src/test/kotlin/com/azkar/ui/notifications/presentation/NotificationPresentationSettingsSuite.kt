package com.azkar.ui.notifications.presentation

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat

/**
 * The persisted form of the presentation settings, asserted through the same encoder the repository
 * uses.
 *
 * This exists because nothing else could have caught the bug it exists to catch. The class carries a
 * `private companion object` for its bounds, the serialization plugin puts the generated serializer
 * outside the class, and the first encode at runtime failed with an `IllegalAccessError` on
 * `Companion` — after compilation succeeded, after every other test passed, and only once a person
 * actually moved a switch in the manager. A round trip through `Json` is the cheapest thing that can
 * prove the access is legal, so it is a test rather than a note in the code.
 *
 * The `Json` configuration is duplicated deliberately: mirroring it is what makes the failure mode
 * reproducible here, and a test that used a laxer configuration than production would prove less.
 */
private val json = Json { ignoreUnknownKeys = true }

val NotificationPresentationSettingsSuite by testSuite("The notification presentation settings") {
    test("they survive a trip through the store") {
        val settings =
            NotificationPresentationSettings(
                systemNotificationsEnabled = true,
                durationSeconds = 25,
                placement = OverlayPlacement.BOTTOM_START,
                maxVisible = 5,
            )

        assertThat(json.decodeFromString<NotificationPresentationSettings>(json.encodeToString(settings)))
            .isEqualTo(settings)
    }

    test("every corner has a name the store can hold") {
        assertThat(OverlayPlacement.entries.map { it.name })
            .containsExactly("TOP_START", "TOP_END", "BOTTOM_START", "BOTTOM_END")
    }

    test("an unset preference reads as the defaults, not as a failure") {
        // What the repository does with a key that was never written, and with a value an older
        // version of the app wrote in a shape this one no longer knows.
        val absent = json.decodeFromString<NotificationPresentationSettings>("""{}""")

        assertThat(absent).isEqualTo(NotificationPresentationSettings())
        assertThat(absent.systemNotificationsEnabled).isFalse()
        assertThat(absent.placement).isEqualTo(OverlayPlacement.TOP_END)
    }

    test("an out-of-range stored value is refused rather than half-accepted") {
        // The bounds are a `require` in the constructor, which is the right place for them: an
        // out-of-range model cannot exist, so nothing downstream has to re-check. The cost is that
        // the stored blob is validated on the way *in*, so a value that could never have been written
        // by this version fails to decode — and the repository's `decode` turns exactly that into
        // "fall back to the defaults", which is what this asserts.
        val stored =
            """{"systemNotificationsEnabled":true,"durationSeconds":9000,"placement":"TOP_END","maxVisible":99}"""

        assertThat(runCatching { json.decodeFromString<NotificationPresentationSettings>(stored) }.getOrNull())
            .isNull()
        // And the fallback is a usable model rather than a blank one, `validated()` included.
        assertThat(NotificationPresentationSettings().validated()).isEqualTo(NotificationPresentationSettings())
    }

    test("an unknown field from a newer version is ignored rather than fatal") {
        val decoded =
            json.decodeFromString<NotificationPresentationSettings>(
                """{"durationSeconds":12,"somethingNewer":true}""",
            )

        assertThat(decoded.durationSeconds).isEqualTo(12)
    }
}
