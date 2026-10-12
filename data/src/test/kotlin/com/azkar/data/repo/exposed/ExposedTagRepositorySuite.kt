package com.azkar.data.repo.exposed

import com.azkar.domain.model.impl.TagImpl
import com.azkar.testUtils.exposedRepositories
import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat

/**
 * Behaviour of the tag queries against a real database, not against a mocked one: case
 * folding, `LIKE` escaping and the ordering guarantees are all implemented in SQL, so an
 * in-memory H2 database is the only place they can honestly be checked.
 *
 * Names are asserted in their stored, folded form — what comes back is lower case however it was
 * asked for. Capitalisation is the presentation layer's business.
 */
val ExposedTagRepositorySuite by testSuite("ExposedTagRepository") {
    exposedRepositories {
        test("save(): persists a new tag and returns it with the generated id") { repositories ->
            val saved = repositories.tags.save(TagImpl(name = "Morning"))

            assertThat(saved.id).describedAs("Generated id should be present after save()").isNotNull()
            assertThat(saved.name).isEqualTo("morning")

            val found = repositories.tags.findById(saved.id)
            assertThat(found).isNotNull()
            assertThat(found?.name).isEqualTo("morning")
        }

        test("findAll(): returns tags ordered by id") { repositories ->
            val a = repositories.tags.save(TagImpl(name = "Alpha"))
            val b = repositories.tags.save(TagImpl(name = "Beta"))
            val c = repositories.tags.save(TagImpl(name = "Gamma"))

            val all = repositories.tags.findAll()

            assertThat(all.map { it.id }).containsExactly(a.id, b.id, c.id)
            assertThat(all.map { it.name }).containsExactly("alpha", "beta", "gamma")
        }

        test("findByNameIgnoreCase(): matches case-insensitively") { repositories ->
            val saved = repositories.tags.save(TagImpl(name = "Evening"))
            assertThat(saved.name).describedAs("The stored form is folded").isEqualTo("evening")

            val lower = repositories.tags.findByNameIgnoreCase("evening")
            val mixed = repositories.tags.findByNameIgnoreCase("EvEnInG")

            assertThat(lower).isNotNull()
            assertThat(lower?.id).isEqualTo(saved.id)
            assertThat(mixed).isNotNull()
            assertThat(mixed?.id).isEqualTo(saved.id)
        }

        testSuite("findByNameContainingIgnoreCase()") {
            test("supports LIKE %term% (case-insensitive)") { repositories ->
                repositories.tags.save(TagImpl(name = "Morning"))
                repositories.tags.save(TagImpl(name = "Noon"))
                repositories.tags.save(TagImpl(name = "Evening"))

                val matches = repositories.tags.findByNameContainingIgnoreCase("ing")

                assertThat(matches.map { it.name }).containsExactlyInAnyOrder("morning", "evening")
            }

            test("escapes LIKE wildcards in the term") { repositories ->
                repositories.tags.save(TagImpl(name = "100% Pure"))
                repositories.tags.save(TagImpl(name = "Nightly"))

                val matches = repositories.tags.findByNameContainingIgnoreCase("0%")

                assertThat(matches.map { it.name }).containsExactly("100% pure")
            }
        }

        test("deleteById(): removes the tag") { repositories ->
            val id = repositories.tags.save(TagImpl(name = "Temp")).id

            repositories.tags.deleteById(id)

            assertThat(repositories.tags.findById(id)).isNull()
        }

        test("delete(Tag): removes when the id is present; no-op when it is not") { repositories ->
            val saved = repositories.tags.save(TagImpl(name = "ToRemove"))
            val id = saved.id

            repositories.tags.delete(saved)
            assertThat(repositories.tags.findById(id)).isNull()

            repositories.tags.delete(TagImpl(name = "DoesNotExist"))

            assertThat(repositories.tags.findByNameIgnoreCase("DoesNotExist")).isNull()
        }
    }
}
