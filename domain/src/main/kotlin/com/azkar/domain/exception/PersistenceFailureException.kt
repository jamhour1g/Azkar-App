package com.azkar.domain.exception

/**
 * Raised when the storage layer itself fails — a dead connection, a constraint violation, a
 * transaction that could not be committed.
 *
 * This is the successor of the `PersistenceException` the old Hibernate layer threw, and it is
 * translated from the driver's exception at exactly one place
 * (`com.azkar.data.repo.exposed.guarded`). A failure that means "no such row" is *not* reported
 * this way: it is a [RemembranceNotFoundException], which is a different answer to a different
 * question.
 */
class PersistenceFailureException(
    message: String,
    cause: Throwable? = null,
) : DataAccessException(message, cause)
