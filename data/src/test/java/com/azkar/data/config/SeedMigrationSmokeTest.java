package com.azkar.data.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.azkar.testUtils.TestJpaManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Seed migration smoke test")
class SeedMigrationSmokeTest {

    @Test
    @DisplayName("Flyway V2 seeds remembrances with Arabic and English translations")
    void seedMigrationPopulatesExpectedData() {
        EntityManagerFactory emf = TestJpaManager.bootstrapWithH2();
        EntityManager em = emf.createEntityManager();

        try {
            long remembranceCount = count(em, "SELECT COUNT(*) FROM remembrance");
            long arabicTranslationCount =
                    count(em, "SELECT COUNT(*) FROM remembrance_translation WHERE locale_code = 'ar'");
            long englishTranslationCount =
                    count(em, "SELECT COUNT(*) FROM remembrance_translation WHERE locale_code = 'en'");
            long muslimDataTagCount = count(em, "SELECT COUNT(*) FROM tag WHERE lower(name) = 'muslim data'");

            assertThat(remembranceCount).isGreaterThanOrEqualTo(250L);
            assertThat(arabicTranslationCount).isGreaterThanOrEqualTo(250L);
            assertThat(englishTranslationCount).isGreaterThanOrEqualTo(250L);
            assertThat(muslimDataTagCount).isEqualTo(1L);
        } finally {
            em.close();
            emf.close();
        }
    }

    private long count(EntityManager em, String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }
}
