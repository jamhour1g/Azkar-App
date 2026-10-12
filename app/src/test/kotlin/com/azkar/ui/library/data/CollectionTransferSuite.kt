package com.azkar.ui.library.data

import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat

val CollectionTransferSuite by testSuite("Moving collections between machines") {
    val morning =
        TransferCollection(
            name = "Morning Azkar",
            description = "What to say between Fajr and Dhuhr.",
            iconKey = "sunrise",
            tagNames = listOf("morning", "sunrise"),
            coverImageBase64 = "aGVsbG8=",
            members =
                listOf(
                    TransferMember("Subhan Allah wa bihamdihi", "Sunan an-Nasa'i"),
                    TransferMember("Alhamdulillah ilallah"),
                ),
        )
    val car = TransferCollection(name = "For the car")

    testSuite("the machine format") {
        test("a collection survives a round trip through it") {
            val restored = CollectionTransfer.decodeJson(CollectionTransfer.encodeJson(listOf(morning)))

            assertThat(restored.getOrThrow()).containsExactly(morning)
        }

        test("a second collection comes back too") {
            val restored = CollectionTransfer.decodeJson(CollectionTransfer.encodeJson(listOf(morning, car)))

            assertThat(restored.getOrThrow()).containsExactly(morning, car)
        }

        test("an empty list round-trips as an empty list") {
            assertThat(CollectionTransfer.decodeJson(CollectionTransfer.encodeJson(emptyList())).getOrThrow()).isEmpty()
        }

        test("the document says which format and version it is") {
            val payload = CollectionTransfer.encodeJson(listOf(morning))

            assertThat(payload).contains("\"format\": \"azkar-collections\"")
            assertThat(payload).contains("\"version\": 1")
        }

        test("Arabic is written as it is, not as escapes") {
            val arabic = TransferCollection(name = "أذكار الصباح", members = listOf(TransferMember("سبحان الله")))

            val payload = CollectionTransfer.encodeJson(listOf(arabic))

            assertThat(payload).contains("أذكار الصباح")
            assertThat(CollectionTransfer.decodeJson(payload).getOrThrow()).containsExactly(arabic)
        }

        test("a field a later build added is ignored rather than rejected") {
            val payload = """{"format":"azkar-collections","version":1,"collections":[],"futureField":"surprise"}"""

            assertThat(CollectionTransfer.decodeJson(payload).getOrThrow()).isEmpty()
        }

        test("a document from a newer version is refused with an explanation") {
            val payload = """{"format":"azkar-collections","version":99,"collections":[]}"""

            val failure = CollectionTransfer.decodeJson(payload).exceptionOrNull()

            assertThat(failure).hasMessageContaining("newer version")
        }

        test("a document from another tool is refused even though it parses") {
            val failure = CollectionTransfer.decodeJson("""{"format":"something-else","version":1}""").exceptionOrNull()

            assertThat(failure).hasMessageContaining("Azkar collections file")
        }

        test("text that is not JSON fails instead of throwing") {
            val failure = CollectionTransfer.decodeJson("this is not a document").exceptionOrNull()

            assertThat(failure).isNotNull()
        }
    }

    testSuite("the shareable format") {
        test("a collection survives a round trip through it, bar its cover") {
            val restored = CollectionTransfer.decodeText(CollectionTransfer.encodeText(listOf(morning)))

            // Everything but the picture. Base64 in a `|`-delimited block a person is expected to
            // read and retype is not a format, so the cover is the one thing this line format
            // leaves behind — and losing it is not a broken import, it is a collection that arrives
            // with the wash its label derives.
            assertThat(restored.getOrThrow())
                .containsExactly(morning.copy(coverImageBase64 = null))
        }

        test("a collection with nothing but a name comes back with its defaults") {
            val restored = CollectionTransfer.decodeText(CollectionTransfer.encodeText(listOf(car))).getOrThrow()

            assertThat(restored.single()).isEqualTo(car)
        }

        test("a member with no source is written without a separator") {
            val payload = CollectionTransfer.encodeText(listOf(morning))

            assertThat(payload.lines()).contains("@member Alhamdulillah ilallah")
            assertThat(payload.lines()).noneMatch { it == "@member Alhamdulillah ilallah|" }
        }

        test("a leading blank line and comments are ignored") {
            val payload =
                """
                # My own list, shared with my sister

                azkar-collections 1
                @collection ${morning.name}
                @member Something | Somewhere
                """.trimIndent()

            val restored = CollectionTransfer.decodeText(payload).getOrThrow()

            assertThat(restored.single().members).containsExactly(TransferMember("Something", "Somewhere"))
        }

        test("a line that is neither a comment nor a directive is ignored") {
            val payload = "azkar-collections 1\nremember to add the travel ones\n@collection Travel"

            assertThat(
                CollectionTransfer
                    .decodeText(payload)
                    .getOrThrow()
                    .single()
                    .name,
            ).isEqualTo("Travel")
        }

        test("a directive this build does not know is refused, and names its line") {
            val payload = "azkar-collections 1\n@collection Travel\n@repeat 3"

            val failure = CollectionTransfer.decodeText(payload).exceptionOrNull()

            assertThat(failure).hasMessageContaining("Line 3")
            assertThat(failure).hasMessageContaining("repeat")
        }

        test("a source that contains the separator still parses") {
            val awkward = TransferCollection("Citations", members = listOf(TransferMember("A | B", "Sahih | Bukhari")))

            val restored = CollectionTransfer.decodeText(CollectionTransfer.encodeText(listOf(awkward))).getOrThrow()

            assertThat(restored.single().members.single()).isEqualTo(awkward.members.single())
        }

        test("a passage that itself contains a bar keeps it") {
            val barred = car.copy(members = listOf(TransferMember("Allah | al-Rahman", "An-Nisa 1:1")))

            val restored = CollectionTransfer.decodeText(CollectionTransfer.encodeText(listOf(barred))).getOrThrow()

            assertThat(restored.single().members.single()).isEqualTo(barred.members.single())
        }

        test("a description with a line break in it survives as one value") {
            val wrapped = morning.copy(description = "First line\nSecond line")

            val restored = CollectionTransfer.decodeText(CollectionTransfer.encodeText(listOf(wrapped))).getOrThrow()

            assertThat(restored.single().description).isEqualTo("First line\nSecond line")
        }

        test("a backslash in a name is not mistaken for an escape") {
            val backslashed = car.copy(name = "Long \\ short")

            val restored =
                CollectionTransfer
                    .decodeText(
                        CollectionTransfer.encodeText(listOf(backslashed)),
                    ).getOrThrow()

            assertThat(restored.single().name).isEqualTo("Long \\ short")
        }

        test("a list from a newer version is refused") {
            val failure = CollectionTransfer.decodeText("azkar-collections 7\n@collection Travel").exceptionOrNull()

            assertThat(failure).hasMessageContaining("newer version")
        }

        test("an empty payload is refused rather than importing nothing silently") {
            assertThat(CollectionTransfer.decodeText("").exceptionOrNull()).isNotNull()
            assertThat(CollectionTransfer.decodeText("\n\n").exceptionOrNull()).isNotNull()
        }

        test("a list from another tool is refused even when it is a plausible header") {
            val failure = CollectionTransfer.decodeText("my-notes 1\n@collection Travel").exceptionOrNull()

            assertThat(failure).hasMessageContaining("Azkar collections list")
        }

        test("two collections in one list do not bleed members into each other") {
            val second = car.copy(members = listOf(TransferMember("Bismillah", "Tirmidhi")))

            val restored =
                CollectionTransfer
                    .decodeText(
                        CollectionTransfer.encodeText(listOf(morning, second)),
                    ).getOrThrow()

            assertThat(restored.first().members).hasSize(2)
            assertThat(restored.last().members).hasSize(1)
        }
    }
}
