package com.azkar.data.repo.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.azkar.data.entity.ExplanationTranslationEntity;
import com.azkar.data.entity.RemembranceEntity;
import com.azkar.data.entity.RemembranceTranslationEntity;
import com.azkar.data.entity.TagEntity;
import com.azkar.testUtils.TestJpaManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Locale;
import org.junit.jupiter.api.*;

@DisplayName("Remembrance ORM Mapping")
class RemembranceOrmMappingTest {

    private EntityManagerFactory emf;
    private EntityManager em;

    @BeforeEach
    void setUp() {
        emf = TestJpaManager.bootstrapWithH2();
        em = emf.createEntityManager();
    }

    @AfterEach
    void tearDown() {
        if (em != null && em.isOpen()) em.close();
        if (emf != null) emf.close();
    }

    @Test
    @DisplayName("Persist graph: remembrance + EN translation/explanation (Map) + existing tags")
    void persistAggregate_withMaps_andManagedTags() {
        // Seed tags as MANAGED (avoid transient tag exception for ManyToMany without cascade
        // PERSIST)
        em.getTransaction().begin();
        TagEntity gold = TagEntity.builder().name("GOLD").build();
        TagEntity silver = TagEntity.builder().name("SILVER").build();
        em.persist(gold);
        em.persist(silver);
        em.getTransaction().commit();

        // Build the root
        RemembranceEntity r =
                RemembranceEntity.builder().favorite(true).source("Abu Huraira").build();

        // Put into the MAPS (text is immutable, so set once on construction)
        RemembranceTranslationEntity enT = RemembranceTranslationEntity.builder()
                .locale(Locale.ENGLISH)
                .text("Allah is the most merciful of the merciful")
                .remembrance(r)
                .build();
        r.getTranslations().put(Locale.ENGLISH, enT);

        ExplanationTranslationEntity enE = ExplanationTranslationEntity.builder()
                .locale(Locale.ENGLISH)
                .text("Allah is the most merciful of the merciful")
                .remembrance(r)
                .build();
        r.getExplanations().put(Locale.ENGLISH, enE);

        // Link managed tags
        r.getTags().add(gold);
        r.getTags().add(silver);

        // Persist + flush
        em.getTransaction().begin();
        em.persist(r);
        em.getTransaction().commit();

        Long id = r.getId();
        assertThat(id).as("ID should be generated").isNotNull();

        // Round-trip verify
        RemembranceEntity reloaded = em.find(RemembranceEntity.class, id);
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.isFavorited()).isTrue();
        assertThat(reloaded.getSource()).isEqualTo("Abu Huraira");

        assertThat(reloaded.getTranslations()).containsKey(Locale.ENGLISH);
        assertThat(reloaded.getTranslations().get(Locale.ENGLISH).getText())
                .isEqualTo("Allah is the most merciful of the merciful");

        assertThat(reloaded.getExplanations()).containsKey(Locale.ENGLISH);
        assertThat(reloaded.getExplanations().get(Locale.ENGLISH).getText())
                .isEqualTo("Allah is the most merciful of the merciful");

        assertThat(reloaded.getTags().stream().map(TagEntity::getName)).containsExactlyInAnyOrder("GOLD", "SILVER");
    }

    @Test
    @DisplayName("Immutable update: replace EN translation map entry (remove + flush + insert new)")
    void updateTranslationByReplacingMapEntry() {
        // Seed a minimal root with EN translation
        RemembranceEntity r = RemembranceEntity.builder()
                .favorite(false)
                .source("Seed Source")
                .build();

        RemembranceTranslationEntity enT = RemembranceTranslationEntity.builder()
                .locale(Locale.ENGLISH)
                .text("Seed text")
                .remembrance(r)
                .build();
        r.getTranslations().put(Locale.ENGLISH, enT);

        em.getTransaction().begin();
        em.persist(r);
        em.getTransaction().commit();

        Long id = r.getId();

        // Replace the EN translation (immutable text => new entity instance)
        em.getTransaction().begin();
        RemembranceEntity managed = em.find(RemembranceEntity.class, id);

        // 1) remove the existing entry
        managed.getTranslations().remove(Locale.ENGLISH);

        // If orphanRemoval=true on the mapping, the row will be deleted at flush/commit.
        // Flushing here ensures UNIQUE(remembrance_id, locale_code) is freed before we insert a new
        // one.
        em.flush();

        // 2) add a brand-new child with updated text
        RemembranceTranslationEntity newEn = RemembranceTranslationEntity.builder()
                .locale(Locale.ENGLISH)
                .text("Updated text")
                .remembrance(managed)
                .build();
        managed.getTranslations().put(Locale.ENGLISH, newEn);

        em.getTransaction().commit();

        // Verify round-trip
        RemembranceEntity after = em.find(RemembranceEntity.class, id);
        assertThat(after.getTranslations()).containsKey(Locale.ENGLISH);
        assertThat(after.getTranslations().get(Locale.ENGLISH).getText()).isEqualTo("Updated text");
    }

    @Test
    @DisplayName("Immutable update: replace EN explanation map entry (remove + flush + insert new)")
    void updateExplanationByReplacingMapEntry() {
        RemembranceEntity r = RemembranceEntity.builder()
                .favorite(false)
                .source("Seed Source")
                .build();

        ExplanationTranslationEntity enE = ExplanationTranslationEntity.builder()
                .locale(Locale.ENGLISH)
                .text("Seed explanation")
                .remembrance(r)
                .build();
        r.getExplanations().put(Locale.ENGLISH, enE);

        em.getTransaction().begin();
        em.persist(r);
        em.getTransaction().commit();

        Long id = r.getId();

        em.getTransaction().begin();
        RemembranceEntity managed = em.find(RemembranceEntity.class, id);

        // Remove, flush, then insert a new immutable explanation
        managed.getExplanations().remove(Locale.ENGLISH);
        em.flush();

        ExplanationTranslationEntity newEnE = ExplanationTranslationEntity.builder()
                .locale(Locale.ENGLISH)
                .text("Updated explanation")
                .remembrance(managed)
                .build();
        managed.getExplanations().put(Locale.ENGLISH, newEnE);

        em.getTransaction().commit();

        RemembranceEntity after = em.find(RemembranceEntity.class, id);
        assertThat(after.getExplanations()).containsKey(Locale.ENGLISH);
        assertThat(after.getExplanations().get(Locale.ENGLISH).getText()).isEqualTo("Updated explanation");
    }
}
