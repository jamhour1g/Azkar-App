package com.azkar.data.repo.exposed

import com.azkar.domain.exception.PersistenceFailureException
import com.azkar.domain.service.TransactionRunner
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

private val logger = KotlinLogging.logger {}

/**
 * Value stamped into the `created_at` / `updated_at` columns.
 *
 * The schema declares those columns `TIMESTAMP WITH TIME ZONE` and Exposed maps them to
 * `OffsetDateTime`, so the current instant is normalized to UTC before it is handed over —
 * otherwise each write would carry the machine's local offset.
 */
internal fun now(): OffsetDateTime = Instant.now().atOffset(ZoneOffset.UTC)

/**
 * Runs [action] and translates a driver failure into the domain's [PersistenceFailureException].
 *
 * Only [ExposedSQLException] is caught: it is the single failure type Exposed raises for a
 * broken statement or connection. Domain failures are runtime exceptions
 * that are not subclasses of it, so they travel straight through and stay matchable by callers.
 *
 * [operation] is a diagnostic, not a sentence anybody reads, and the distinction is worth keeping
 * in both directions. It is written in English, and it quotes row ids, so nothing may render it:
 * the UI answers every failure with one localized sentence per outcome and has no branch that
 * looks at this message. It rides on the exception because that is where a fault this specific
 * belongs — a driver failure nobody has attributed to an operation is just a stack trace.
 *
 * The failure is logged here, before it is translated, because this is the last point at which the
 * operation is still known. The exception alone says that a statement failed, not which, and a
 * caller that discards the outcome — stamping a collection's `last_opened_at`, say, which nothing
 * watches the result of — would otherwise take the fault out of the log entirely. The line does
 * duplicate what a caller that *does* handle the failure writes with the same cause attached; that
 * duplication is the price of the guarantee, and the cheaper side of it.
 */
internal fun <T> guarded(
    operation: String,
    action: () -> T,
): T =
    try {
        action()
    } catch (exception: ExposedSQLException) {
        logger.warn(exception) { operation }
        throw PersistenceFailureException(operation, exception)
    }

/**
 * Runs [block] in a transaction, translating a driver failure into [PersistenceFailureException].
 *
 * Every repository method in this package is one of these, which is why the pair is offered as one
 * call rather than as the nested shape the repositories used to spell out:
 *
 * ```
 * transactions.run { guarded("Failed to ...") { statement() } }
 * ```
 *
 * The nesting carries no meaning that the single call does not already carry — the order is fixed,
 * every site does it the same way, and the only reason it was written longhand was that the reader
 * should not have to know a helper existed. Naming it keeps the failure translation in exactly one
 * place and leaves a repository method as the one line that says what the domain asked for.
 *
 * [block] is deliberately not `suspend`. Nothing inside a repository statement should be able to
 * suspend between two statements of one transaction, and a non-suspending parameter is what makes
 * that structural rather than a matter of care. A caller that needs to await mid-transaction is
 * doing something this layer does not model.
 *
 * Returns whatever [block] returns, which is how the reads return their rows and the writes return
 * their counts.
 */
internal suspend fun <T> TransactionRunner.tx(
    operation: String,
    block: () -> T,
): T = run { guarded(operation, block) }
