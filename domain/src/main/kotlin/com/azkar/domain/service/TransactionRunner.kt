package com.azkar.domain.service

/**
 * Runs [block] inside a storage transaction, committing it on success and rolling it back when
 * [block] throws.
 *
 * The domain owns the *decision* to make a group of writes atomic; it does not own the mechanism,
 * so this stays a port rather than a concrete transaction manager. `com.azkar.data` supplies the
 * implementation.
 *
 * This deliberately adds no error handling of its own. Translating a driver failure into a
 * [com.azkar.domain.exception.DataAccessException] is the data layer's job, and doing it in two
 * places would only create two chances to disagree about what a failure means.
 */
interface TransactionRunner {
    /** Runs [block] in a transaction and returns its result. */
    suspend fun <T> run(block: suspend () -> T): T
}
