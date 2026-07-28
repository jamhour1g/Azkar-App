package com.azkar.data.config;

import com.azkar.data.repo.jpa.JpaRepositoryBundle;
import com.azkar.data.transaction.JpaTransactionRunner;
import com.azkar.domain.service.RemembranceService;
import com.azkar.domain.service.TagService;
import jakarta.persistence.EntityManager;

public final class DomainServiceContext implements AutoCloseable {

    private final EntityManager entityManager;
    private final JpaRepositoryBundle repositoryBundle;
    private final RemembranceService remembranceService;
    private final TagService tagService;
    private boolean closed;

    public DomainServiceContext() {
        entityManager = JpaManager.getInstance().getEntityManager();
        repositoryBundle = new JpaRepositoryBundle(entityManager);

        var transactionRunner = new JpaTransactionRunner(entityManager);
        remembranceService = new RemembranceService(repositoryBundle.remembranceRepository(), transactionRunner);
        tagService = new TagService(repositoryBundle.tagRepository(), transactionRunner);
    }

    public RemembranceService remembranceService() {
        return remembranceService;
    }

    public TagService tagService() {
        return tagService;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        repositoryBundle.close();
        if (entityManager.isOpen()) {
            entityManager.close();
        }
    }
}
