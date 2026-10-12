package com.azkar.ui.library.data

import com.azkar.domain.model.Remembrance
import com.azkar.ui.library.customCollection
import com.azkar.ui.library.officialCollection
import com.azkar.ui.library.remembrance
import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat
import java.util.Locale

val CollectionImportSuite by testSuite("Working out what an import will do") {
    val morning = remembrance(1L, text = MORNING_TEXT, source = "Bukhari")
    val evening = remembrance(2L, text = "Say the evening remembrance", source = "Muslim")
    val unlisted = remembrance(3L, text = "A passage with no citation")

    testSuite("a free name creates a collection") {
        test("a payload with no shelf of that name creates one") {
            val plan = CollectionImport.plan(listOf(exportOf("For the car", morning)), emptyList(), catalogue(morning))

            val created = plan.plans.single() as ImportPlan.Create
            assertThat(created.memberIds).containsExactly(1L)
            assertThat(plan.created).isEqualTo(1)
            assertThat(plan.merged).isZero()
        }

        test("a created collection keeps the payload's description and icon") {
            val plan =
                CollectionImport.plan(
                    listOf(exportOf("For the car", morning, description = "Long drives.", iconKey = "compass")),
                    emptyList(),
                    catalogue(morning),
                )

            val created = plan.plans.single() as ImportPlan.Create
            assertThat(created.transfer.description).isEqualTo("Long drives.")
            assertThat(created.transfer.iconKey).isEqualTo("compass")
        }

        test("a payload with no members still creates the collection") {
            val plan = CollectionImport.plan(listOf(exportOf("Empty", morning)), emptyList(), emptyList())

            assertThat(plan.created).isEqualTo(1)
            assertThat((plan.plans.single() as ImportPlan.Create).memberIds).isEmpty()
        }
    }

    testSuite("a name that already exists is merged into") {
        test("a custom collection of the same name is added to, not duplicated") {
            val existing = customCollection(10L, "For the car", listOf(evening))

            val plan =
                CollectionImport.plan(
                    listOf(exportOf("For the car", morning)),
                    listOf(existing),
                    catalogue(morning, evening),
                )

            val merged = plan.plans.single() as ImportPlan.Merge
            assertThat(merged.collectionId).isEqualTo(10L)
            assertThat(merged.memberIds).containsExactly(1L)
            assertThat(plan.merged).isEqualTo(1)
            assertThat(plan.created).isZero()
        }

        test("the name is compared without regard to case or surrounding space") {
            val existing = customCollection(10L, "For the car", emptyList())

            val plan =
                CollectionImport.plan(
                    listOf(exportOf("  FOR THE CAR ", morning)),
                    listOf(existing),
                    catalogue(morning),
                )

            assertThat(plan.plans.single()).isInstanceOf(ImportPlan.Merge::class.java)
        }

        test("a merge resolves a member the collection already holds, leaving the add to filter it") {
            val existing = customCollection(10L, "For the car", listOf(morning))

            val plan =
                CollectionImport.plan(
                    listOf(exportOf("For the car", morning)),
                    listOf(existing),
                    catalogue(morning),
                )

            // The plan cannot know what is already a member — the merge has not run — so it resolves
            // honestly and lets the repository's own filter drop the duplicate.
            assertThat((plan.plans.single() as ImportPlan.Merge).memberIds).containsExactly(1L)
        }
    }

    testSuite("a collection we ship is never touched") {
        test("a name matching a shipped collection is skipped") {
            val existing = officialCollection(20L, "Morning Azkar", members = listOf(morning))

            val plan =
                CollectionImport.plan(
                    listOf(exportOf("Morning Azkar", morning)),
                    listOf(existing),
                    catalogue(morning),
                )

            assertThat(plan.plans.single()).isInstanceOf(ImportPlan.SkipOfficial::class.java)
            assertThat(plan.skippedOfficial).containsExactly("Morning Azkar")
            assertThat(plan.membersAdded).isZero()
        }

        test("the shipped name is matched without regard to case") {
            val existing = officialCollection(20L, "Morning Azkar")

            val plan =
                CollectionImport.plan(
                    listOf(exportOf("MORNING AZKAR", morning)),
                    listOf(existing),
                    catalogue(morning),
                )

            assertThat(plan.skippedOfficial).containsExactly("MORNING AZKAR")
        }

        test("a shipped collection is skipped even when a custom one of that name also exists") {
            val shipped = officialCollection(20L, "Morning Azkar")
            val mine = customCollection(10L, "Morning Azkar", emptyList())

            val plan =
                CollectionImport.plan(
                    listOf(exportOf("Morning Azkar", morning)),
                    listOf(mine, shipped),
                    catalogue(morning),
                )

            assertThat(plan.plans.single()).isInstanceOf(ImportPlan.Merge::class.java)
            assertThat((plan.plans.single() as ImportPlan.Merge).collectionId).isEqualTo(10L)
        }
    }

    testSuite("finding the passages") {
        test("a passage is found by its text and citation together") {
            val plan =
                CollectionImport.plan(
                    listOf(transfer("Car", member(MORNING_TEXT, "Bukhari"))),
                    emptyList(),
                    catalogue(morning),
                )

            assertThat((plan.plans.single() as ImportPlan.Create).memberIds).containsExactly(1L)
        }

        test("a passage whose citation was reworded is still found by its text") {
            val plan =
                CollectionImport.plan(
                    listOf(transfer("Car", member(MORNING_TEXT, "Sahih al-Bukhari"))),
                    emptyList(),
                    catalogue(morning),
                )

            assertThat((plan.plans.single() as ImportPlan.Create).memberIds).containsExactly(1L)
        }

        test("a passage with no citation in the payload matches a cited one") {
            val plan =
                CollectionImport.plan(
                    listOf(transfer("Car", member(MORNING_TEXT))),
                    emptyList(),
                    catalogue(morning),
                )

            assertThat((plan.plans.single() as ImportPlan.Create).memberIds).containsExactly(1L)
        }

        test("a passage is found in a locale other than the one it was exported in") {
            val plan =
                CollectionImport.plan(
                    listOf(transfer("Car", member(EVENING_ARABIC, "Muslim"))),
                    emptyList(),
                    catalogue(morning, evening),
                )

            assertThat((plan.plans.single() as ImportPlan.Create).memberIds).containsExactly(2L)
        }

        test("whitespace and case in the payload are forgiven") {
            val plan =
                CollectionImport.plan(
                    listOf(transfer("Car", member("  SAY THE   MORNING   REMEMBRANCE ", "bukhari"))),
                    emptyList(),
                    catalogue(morning),
                )

            assertThat((plan.plans.single() as ImportPlan.Create).memberIds).containsExactly(1L)
        }

        test("a passage not in the catalogue is reported rather than invented") {
            val plan =
                CollectionImport.plan(
                    listOf(transfer("Car", member("A line I typed myself"))),
                    emptyList(),
                    catalogue(morning),
                )

            assertThat(plan.unmatched.map { it.text }).containsExactly("A line I typed myself")
            assertThat((plan.plans.single() as ImportPlan.Create).memberIds).isEmpty()
        }

        test("a citation-less passage matches one with a citation") {
            val plan =
                CollectionImport.plan(
                    listOf(transfer("Car", member("A passage with no citation"))),
                    emptyList(),
                    catalogue(unlisted),
                )

            assertThat((plan.plans.single() as ImportPlan.Create).memberIds).containsExactly(3L)
        }

        test("the same passage twice in one payload is counted once") {
            val plan =
                CollectionImport.plan(
                    listOf(
                        transfer(
                            "Car",
                            member(MORNING_TEXT, "Bukhari"),
                            member("say the morning   REMEMBRANCE", "bukhari"),
                        ),
                    ),
                    emptyList(),
                    catalogue(morning),
                )

            assertThat((plan.plans.single() as ImportPlan.Create).memberIds).containsExactly(1L)
            assertThat(plan.membersAdded).isEqualTo(1)
        }

        test("a collection is found even when it has no citation of its own") {
            val plan =
                CollectionImport.plan(
                    listOf(transfer("Car", member(MORNING_TEXT, "Bukhari"))),
                    emptyList(),
                    listOf(remembrance(1L, text = MORNING_TEXT)),
                )

            assertThat((plan.plans.single() as ImportPlan.Create).memberIds).containsExactly(1L)
        }

        test("a remembrance that was never persisted is not offered as a match") {
            val plan =
                CollectionImport.plan(
                    listOf(transfer("Car", member("Say the morning remembrance", "Sunan an-Nasa'i"))),
                    emptyList(),
                    listOf(remembrance(null, text = MORNING_TEXT)),
                )

            assertThat((plan.plans.single() as ImportPlan.Create).memberIds).isEmpty()
        }
    }

    testSuite("the whole payload") {
        test("several collections are planned independently") {
            val existing = customCollection(10L, "For the car", emptyList())

            val plan =
                CollectionImport.plan(
                    listOf(exportOf("For the car", morning), exportOf("Travel", evening)),
                    listOf(existing),
                    catalogue(morning, evening),
                )

            assertThat(plan.created).isEqualTo(1)
            assertThat(plan.merged).isEqualTo(1)
            assertThat(plan.membersAdded).isEqualTo(2)
        }

        test("an empty payload plans nothing and is not an error") {
            val plan = CollectionImport.plan(emptyList(), emptyList(), emptyList())

            assertThat(plan.isEmpty).isTrue()
        }

        test("a payload with no labels at all still plans a create") {
            val plan =
                CollectionImport.plan(
                    listOf(transfer("Car", member(MORNING_TEXT))),
                    emptyList(),
                    catalogue(morning),
                )

            assertThat(plan.created).isEqualTo(1)
        }
    }
}

private const val MORNING_TEXT = "Say the morning remembrance"
private const val EVENING_ARABIC = "ذكر 2"

private fun catalogue(vararg remembrances: Remembrance): List<Remembrance> = remembrances.toList()

private fun member(
    text: String,
    source: String? = null,
): TransferMember = TransferMember(text = text, source = source)

/** A payload carrying exactly the members it was handed, which is how a hand-written list arrives. */
private fun transfer(
    name: String,
    vararg members: TransferMember,
    description: String? = null,
    iconKey: String? = null,
    tagNames: List<String> = emptyList(),
): TransferCollection =
    TransferCollection(
        name = name,
        description = description,
        iconKey = iconKey,
        tagNames = tagNames,
        members = members.toList(),
    )

/**
 * A payload quoted the way a real export would quote [remembrances]: the translation text and the
 * citation, never the id — which is the whole reason the import matches on text.
 */
private fun exportOf(
    name: String,
    vararg remembrances: Remembrance,
    description: String? = null,
    iconKey: String? = null,
    tagNames: List<String> = emptyList(),
): TransferCollection =
    transfer(
        name = name,
        description = description,
        iconKey = iconKey,
        tagNames = tagNames,
        members = remembrances.map { it.toTransferMember() }.toTypedArray(),
    )

private fun Remembrance.toTransferMember(): TransferMember =
    TransferMember(
        text = requireNotNull(translations[Locale.ENGLISH]).translationPair.text,
        source = source,
    )
