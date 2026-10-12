package com.azkar.domain.exception

/**
 * Base type for every failure the domain layer reports.
 *
 * It is a [RuntimeException] on purpose. Nothing in this module is expected to force callers to
 * catch it, and letting it stay unchecked is what keeps the ports free of `throws` clauses: the
 * data layer decides what a failure *means*, and the UI decides what to show, but neither is
 * obliged to enumerate every possible failure mode just to compile.
 */
open class DataAccessException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
