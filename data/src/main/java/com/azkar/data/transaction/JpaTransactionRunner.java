package com.azkar.data.transaction;

import com.azkar.domain.exception.PersistenceFailureException;
import com.azkar.domain.service.TransactionRunner;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import java.util.function.Supplier;

/// JPA-backed [TransactionRunner] that manages [EntityTransaction] lifecycle.
///
/// Begins a transaction before the action, commits on success, and rolls
/// back on any exception. If the action throws a checked or unchecked
/// exception, it is re-thrown after rollback. JPA-specific exceptions
/// are wrapped in [PersistenceFailureException] to keep the domain layer
/// persistence-agnostic.
public final class JpaTransactionRunner implements TransactionRunner {

    private final EntityManager em;

    public JpaTransactionRunner(EntityManager em) {
        this.em = em;
    }

    @Override
    public <T> T run(Supplier<T> action) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            T result = action.get();
            tx.commit();
            return result;
        } catch (Exception e) {
            rollbackSafely(tx);
            throw wrapIfNeeded(e);
        }
    }

    @Override
    public void run(Runnable action) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            action.run();
            tx.commit();
        } catch (Exception e) {
            rollbackSafely(tx);
            throw wrapIfNeeded(e);
        }
    }

    private static void rollbackSafely(EntityTransaction tx) {
        if (tx.isActive()) {
            tx.rollback();
        }
    }

    /// Re-throws domain exceptions as-is; wraps unexpected JPA/JDBC
    /// exceptions in [PersistenceFailureException].
    private static RuntimeException wrapIfNeeded(Exception e) {
        if (e instanceof RuntimeException re) {
            return re;
        }
        return new PersistenceFailureException("Transaction failed", e);
    }
}
