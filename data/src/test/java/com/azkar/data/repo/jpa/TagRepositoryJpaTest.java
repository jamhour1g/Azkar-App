package com.azkar.data.repo.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.azkar.domain.model.Tag;
import com.azkar.domain.model.impl.TagImpl;
import com.azkar.domain.repo.TagRepository;
import com.azkar.testUtils.TestJpaManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Optional;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TagRepositoryAdapter")
class TagRepositoryJpaTest {

    private EntityManagerFactory emf;
    private EntityManager em;
    private StatelessSession statelessSession;
    private TagRepository repo;

    @BeforeEach
    void setUp() {
        emf = TestJpaManager.bootstrapWithH2();
        em = emf.createEntityManager();
        statelessSession = emf
            .unwrap(SessionFactory.class)
            .openStatelessSession();
        var dataRepo = new TagDataRepository_(statelessSession);
        repo = new TagRepositoryAdapter(dataRepo, em);
    }

    @AfterEach
    void tearDown() {
        if (statelessSession != null) statelessSession.close();
        if (em != null && em.isOpen()) em.close();
        if (emf != null) emf.close();
    }

    private Tag saveInTransaction(Tag tag) {
        em.getTransaction().begin();
        Tag saved = repo.save(tag);
        em.getTransaction().commit();
        return saved;
    }

    @Test
    @DisplayName(
        "save(): persists new TagImpl and returns Tag with generated id"
    )
    void save_persistsAndReturnsWithId() {
        Tag t = TagImpl.builder().name("Morning").build();

        Tag saved = saveInTransaction(t);

        assertThat(saved.getId())
            .as("Generated id should be present after save()")
            .isPresent();
        assertThat(saved.getName()).isEqualTo("Morning");

        Optional<Tag> found = repo.findById(saved.getId().get());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Morning");
    }

    @Test
    @DisplayName("findAll(): returns tags ordered by id")
    void findAll_ordersById() {
        Tag a = saveInTransaction(TagImpl.builder().name("Alpha").build());
        Tag b = saveInTransaction(TagImpl.builder().name("Beta").build());
        Tag c = saveInTransaction(TagImpl.builder().name("Gamma").build());

        List<Tag> all = repo.findAll();

        assertThat(all)
            .extracting(Tag::getId)
            .as("IDs in insertion order")
            .containsExactly(a.getId(), b.getId(), c.getId());

        assertThat(all)
            .extracting(Tag::getName)
            .containsExactly("Alpha", "Beta", "Gamma");
    }

    @Test
    @DisplayName("findByNameIgnoreCase(): matches case-insensitively")
    void findByNameIgnoreCase_matchesCaseInsensitively() {
        Tag saved = saveInTransaction(
            TagImpl.builder().name("Evening").build()
        );

        Optional<Tag> lower = repo.findByNameIgnoreCase("evening");
        Optional<Tag> mixed = repo.findByNameIgnoreCase("EvEnInG");

        assertThat(lower).isPresent();
        assertThat(lower.get().getId()).isEqualTo(saved.getId());

        assertThat(mixed).isPresent();
        assertThat(mixed.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName(
        "findByNameContainingIgnoreCase(): supports LIKE %term% (case-insensitive)"
    )
    void findByNameContainingIgnoreCase_likeContains() {
        saveInTransaction(TagImpl.builder().name("Morning").build());
        saveInTransaction(TagImpl.builder().name("Noon").build());
        saveInTransaction(TagImpl.builder().name("Evening").build());

        List<Tag> matches = repo.findByNameContainingIgnoreCase("ing");

        assertThat(matches)
            .extracting(Tag::getName)
            .containsExactlyInAnyOrder("Morning", "Evening");
    }

    @Test
    @DisplayName("deleteById(): removes the tag")
    void deleteById_removesRow() {
        Tag saved = saveInTransaction(TagImpl.builder().name("Temp").build());
        Long id = saved.getId().orElseThrow();

        em.getTransaction().begin();
        repo.deleteById(id);
        em.getTransaction().commit();

        assertThat(repo.findById(id)).isEmpty();
    }

    @Test
    @DisplayName(
        "delete(Tag): removes when id is present; no-op if id is empty"
    )
    void delete_withDomainArg() {
        Tag saved = saveInTransaction(
            TagImpl.builder().name("ToRemove").build()
        );
        Long id = saved.getId().orElseThrow();

        // delete existing
        em.getTransaction().begin();
        repo.delete(saved);
        em.getTransaction().commit();
        assertThat(repo.findById(id)).isEmpty();

        // delete with empty id should be a no-op
        Tag transientTag = TagImpl.builder().name("DoesNotExist").build();
        em.getTransaction().begin();
        repo.delete(transientTag); // should not throw
        em.getTransaction().commit();

        assertThat(repo.findByNameIgnoreCase("DoesNotExist")).isEmpty();
    }
}
