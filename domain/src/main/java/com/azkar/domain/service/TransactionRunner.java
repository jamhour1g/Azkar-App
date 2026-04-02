package com.azkar.domain.service;

import java.util.function.Supplier;

/// Abstraction for running code inside a transaction.
///
/// The domain layer uses this interface to demarcate transactional
/// boundaries without depending on any persistence technology.
/// The concrete implementation (e.g., JPA-based) lives in the data
/// module.
public interface TransactionRunner {
    /// Executes the given action inside a transaction and returns its result.
    ///
    /// If the action completes normally the transaction is committed;
    /// if it throws, the transaction is rolled back and the exception
    /// is re-thrown.
    ///
    /// @param <T>    the return type of the action
    /// @param action the work to execute transactionally
    /// @return the result produced by the action
    <T> T run(Supplier<T> action);

    /// Executes the given action inside a transaction (no return value).
    ///
    /// Same commit/rollback semantics as [#run(Supplier)].
    ///
    /// @param action the work to execute transactionally
    void run(Runnable action);
}
