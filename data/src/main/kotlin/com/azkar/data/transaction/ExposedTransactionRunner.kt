package com.azkar.data.transaction

import com.azkar.domain.service.TransactionRunner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

/**
 * [TransactionRunner] backed by Exposed.
 *
 * Exposed's `suspendTransaction { }` block commits on normal return and rolls back on throw, so
 * this is a thin adapter with no error handling of its own — which is deliberate:
 *
 *  - a failure raised by a repository has already been translated into
 *    `PersistenceFailureException`, so there is nothing here left to wrap;
 *  - a domain failure such as `RemembranceNotFoundException` is a runtime exception callers
 *    match on, and rewrapping it would only hide it;
 *  - Exposed reports a broken statement or connection as `ExposedSQLException`, which is
 *    itself a runtime exception and already travels out of the block untouched.
 *
 * ### Why this looks the way it does
 *
 * `suspendTransaction` is Exposed's own suspending entry point. Its `statement` lambda is a
 * `suspend` function type, so [block] is handed to it directly without a thread hop to keep a
 * thread-local alive.
 *
 * The transaction itself is bound to the *coroutine context* rather than to a thread, so it
 * survives any dispatcher change inside [block] instead of only working while execution
 * happens to stay put. `withContext(ioDispatcher)` is therefore a plain "get this off the
 * caller's thread" hop: the transaction travels with the coroutine into it and comes back out.
 *
 * A nested call — a service wrapping a repository, say — needs no special case. `suspendTransaction`
 * finds the caller's transaction in the coroutine context and joins it instead of opening a
 * second one against the same rows, and because `useNestedTransactions` is off, the nested block
 * shares its parent's connection and its rollback propagates outward.
 *
 * [ioDispatcher] is a constructor parameter for the same reason it is one everywhere else here:
 * a hard-coded `Dispatchers.IO` cannot be replaced from a test, so a test that wanted to assert
 * on the hop would have to be a test of the real thread pool. The default keeps every production
 * call site unchanged.
 */
class ExposedTransactionRunner(
    private val database: Database,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T =
        withContext(ioDispatcher) { suspendTransaction(database) { block() } }
}
