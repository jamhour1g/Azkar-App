package com.azkar.data.repo.jpa;

import com.azkar.domain.repo.RemembranceRepository;
import com.azkar.domain.repo.TagRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;

public final class JpaRepositoryBundle implements AutoCloseable {

    private final StatelessSession statelessSession;
    private final RemembranceRepository remembranceRepository;
    private final TagRepository tagRepository;

    public JpaRepositoryBundle(EntityManager entityManager) {
        SessionFactory sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
        statelessSession = sessionFactory.openStatelessSession();
        remembranceRepository =
                new RemembranceRepositoryAdapter(new RemembranceDataRepository_(statelessSession), entityManager);
        tagRepository = new TagRepositoryAdapter(new TagDataRepository_(statelessSession), entityManager);
    }

    public RemembranceRepository remembranceRepository() {
        return remembranceRepository;
    }

    public TagRepository tagRepository() {
        return tagRepository;
    }

    @Override
    public void close() {
        statelessSession.close();
    }
}
