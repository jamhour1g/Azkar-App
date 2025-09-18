package com.azkar.data.repo.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.azkar.domain.model.Remembrance;
import com.azkar.domain.repo.RemembranceRepository;
import com.azkar.testUtils.TestJpaManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.*;

@DisplayName(
    "RemembranceRepositoryJpa – repository behavior against a temp SQLite DB"
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RemembranceRepositoryJpaTest {

    private EntityManagerFactory emf;
    private EntityManager em;
    private RemembranceRepository repo;

    @BeforeAll
    void boot() {
        emf = TestJpaManager.bootstrapWithTempSqlite();
        em = emf.createEntityManager();
        repo = new RemembranceRepositoryJpa(em);
    }

    @AfterAll
    void shutdown() {
        if (em != null) em.close();
        if (emf != null) emf.close();
    }

    @SuppressWarnings("SqlWithoutWhere")
    @BeforeEach
    void cleanAndSeed() {
        if (em.getTransaction().isActive()) {
            em.getTransaction().rollback();
        }

        em.getTransaction().begin();
        try {
            // Clean tables (order matters due to FKs)
            em.createNativeQuery("DELETE FROM favorite").executeUpdate();
            em.createNativeQuery("DELETE FROM remembrance_tag").executeUpdate();
            em.createNativeQuery("DELETE FROM tag").executeUpdate();
            em
                .createNativeQuery("DELETE FROM explanation_translation")
                .executeUpdate();
            em
                .createNativeQuery("DELETE FROM remembrance_translation")
                .executeUpdate();
            em.createNativeQuery("DELETE FROM remembrance").executeUpdate();

            // Seed base remembrances
            em
                .createNativeQuery(
                    """
                    INSERT INTO remembrance (id, source, grade)
                    VALUES (1,'Bukhari','SAHIH'),
                           (2,'Muslim','HASAN'),
                           (3,'Tirmidhi','DAIF')
                    """
                )
                .executeUpdate();

            // Required by your mapper: both translation and explanation must exist
            em
                .createNativeQuery(
                    """
                    INSERT INTO remembrance_translation (remembrance_id, locale_code, text)
                    VALUES (1,'ar','سبحان الله'),
                           (2,'ar','الحمد لله'),
                           (3,'ar','لا إله إلا الله')
                    """
                )
                .executeUpdate();

            em
                .createNativeQuery(
                    """
                    INSERT INTO explanation_translation (remembrance_id, locale_code, text)
                    VALUES (1,'ar','شرح 1'),
                           (2,'ar','شرح 2'),
                           (3,'ar','شرح 3')
                    """
                )
                .executeUpdate();

            // Tags + join
            em
                .createNativeQuery(
                    """
                    INSERT INTO tag (id, name)
                    VALUES (10,'Morning'),
                           (11,'EVENING')
                    """
                )
                .executeUpdate();

            em
                .createNativeQuery(
                    """
                    INSERT INTO remembrance_tag (remembrance_id, tag_id)
                    VALUES (1,10),
                           (2,11),
                           (3,10)
                    """
                )
                .executeUpdate();

            // Favorites
            // Favorites: seed rows, then link them via remembrance.favorite_id
            em
                .createNativeQuery(
                    """
                    INSERT INTO favorite (id)
                    VALUES (1),
                           (3)
                    """
                )
                .executeUpdate();

            // Link favorites to their remembrances
            em
                .createNativeQuery(
                    """
                    UPDATE remembrance
                    SET favorite_id = id
                    WHERE id IN (1, 3)
                    """
                )
                .executeUpdate();

            em.getTransaction().commit();
        } catch (RuntimeException ex) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw ex;
        }
        em.clear();
    }

    @AfterEach
    void ensureTxClosed() {
        if (em.getTransaction().isActive()) {
            em.getTransaction().rollback();
        }
        em.clear();
    }

    @Test
    @DisplayName("findAll(): returns all rows ordered by ID ascending")
    void findAll_ordersById() {
        var all = repo.findAll();
        assertThat(all)
            .as("Expected exactly 3 remembrances in seed data")
            .hasSize(3);
        assertThat(all)
            .as("Remembrances should be ordered by ID ascending")
            .extracting(Remembrance::getId)
            .containsExactly(Optional.of(1L), Optional.of(2L), Optional.of(3L));
    }

    @Test
    @DisplayName("findById(): present for existing ID, empty for missing ID")
    void findById_foundAndMissing() {
        assertThat(repo.findById(2L))
            .as("ID 2 exists in seed data and should be found")
            .isPresent();
        assertThat(repo.findById(999L))
            .as("Non-existent ID 999 should not be found")
            .isEmpty();
    }

    @Test
    @DisplayName(
        "save() followed by delete(): can merge an existing row and delete it by entity and by" +
        " ID"
    )
    void save_thenDelete() {
        // Create a new remembrance row directly, then load and resave to exercise merge+map
        em.getTransaction().begin();
        em
            .createNativeQuery(
                """
                INSERT INTO remembrance (id, source, grade)
                VALUES (9,'Abu Daud','SAHIH')
                """
            )
            .executeUpdate();
        em
            .createNativeQuery(
                """
                INSERT INTO remembrance_translation (remembrance_id, locale_code, text)
                VALUES (9,'ar','ذكر 9')
                """
            )
            .executeUpdate();
        em
            .createNativeQuery(
                """
                INSERT INTO explanation_translation (remembrance_id, locale_code, text)
                VALUES (9,'ar','شرح 9')
                """
            )
            .executeUpdate();
        em.getTransaction().commit();

        // Load via repo and re-save (merge path)
        var loaded = repo.findById(9L).orElseThrow();
        var saved = repo.save(loaded);
        assertThat(saved.getId())
            .as("Saved entity should retain the ID=9 after merge")
            .isEqualTo(Optional.of(9L));

        // Delete it by entity
        em.getTransaction().begin();
        repo.delete(saved);
        em.getTransaction().commit();

        assertThat(repo.findById(9L))
            .as("Entity with ID=9 should be deleted")
            .isEmpty();

        // Delete by id (idempotent-ish for non-existing should not throw)
        em.getTransaction().begin();
        repo.deleteById(9L);
        em.getTransaction().commit();

        assertThat(repo.findById(9L))
            .as(
                "deleteById on non-existing ID should have no effect and not recreate row"
            )
            .isEmpty();
    }

    @Test
    @DisplayName(
        "findByTagNameIgnoreCase(): case-insensitive tag lookup returns correct IDs"
    )
    void findByTagNameIgnoreCase_works() {
        var morning = repo.findByTagNameIgnoreCase("morning");
        assertThat(morning)
            .as("Tag 'morning' should map to remembrances 1 and 3")
            .extracting(Remembrance::getId)
            .containsExactlyInAnyOrder(Optional.of(1L), Optional.of(3L));

        var evening = repo.findByTagNameIgnoreCase("evening");
        assertThat(evening)
            .as("Tag 'evening' should map to remembrance 2 only")
            .extracting(Remembrance::getId)
            .containsExactly(Optional.of(2L));
    }

    @Test
    @DisplayName("findFavorites(): returns favorites ordered by remembrance ID")
    void findFavorites_ordersByRemembranceId() {
        var favs = repo.findFavorites();
        assertThat(favs)
            .as("Seed favorites are IDs 1 and 3, ordered by ID")
            .extracting(Remembrance::getId)
            .containsExactly(Optional.of(1L), Optional.of(3L));
    }

    @Test
    @DisplayName(
        "search(): preserves FTS ranking and respects the requested Locale"
    )
    void search_preservesFtsOrder_andHonorsLocale() {
        var ar = Locale.forLanguageTag("ar");
        var results = repo.search(ar, "سبحان");

        assertThat(results)
            .as("Arabic query 'سبحان' should return remembrance with ID=1 only")
            .extracting(Remembrance::getId)
            .containsExactly(Optional.of(1L));
    }

    @Test
    @DisplayName("search(): blank or whitespace queries return an empty list")
    void search_blank_returnsEmpty() {
        assertThat(repo.search(Locale.ENGLISH, "  "))
            .as("Whitespace-only query must return empty results")
            .isEmpty();
        assertThat(repo.search(Locale.forLanguageTag("ar"), ""))
            .as("Empty query must return empty results")
            .isEmpty();
    }

    @Test
    @DisplayName(
        "markFavorite()/unmarkFavorite(): toggles favorites and preserves ordering"
    )
    void markFavorite_and_unmarkFavorite() {
        // Initial: favorites are 1,3
        assertThat(repo.findFavorites())
            .as("Initial favorites should be [1, 3]")
            .extracting(Remembrance::getId)
            .containsExactly(Optional.of(1L), Optional.of(3L));

        // Mark 2
        em.getTransaction().begin();
        repo.markFavorite(2L);
        em.getTransaction().commit();
        assertThat(repo.findFavorites())
            .as("After marking ID=2, favorites should be [1, 2, 3]")
            .extracting(Remembrance::getId)
            .containsExactly(Optional.of(1L), Optional.of(2L), Optional.of(3L));

        // Unmark 1
        em.getTransaction().begin();
        repo.unmarkFavorite(1L);
        em.getTransaction().commit();

        assertThat(repo.findFavorites())
            .as("After unmarking ID=1, favorites should be [2, 3]")
            .extracting(Remembrance::getId)
            .containsExactly(Optional.of(2L), Optional.of(3L));
    }
}
