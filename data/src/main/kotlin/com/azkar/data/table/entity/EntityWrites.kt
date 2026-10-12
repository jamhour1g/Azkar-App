package com.azkar.data.table.entity

import org.jetbrains.exposed.v1.dao.Entity

/**
 * Applies [change] to this row and writes it out before returning.
 *
 * Exposed holds a mutated entity in memory and emits its `UPDATE` at the transaction's flush hook,
 * which is the right default for code that ends the transaction there and then forgets about the
 * entity. It is the wrong default for the writers in this module, because every one of them reads
 * back inside the same transaction as the write — a `save` that renames a collection and immediately
 * re-reads it, a reminder tick that stamps a firing and then lists the schedule. A reread goes to
 * the database rather than to this object, so without the explicit flush it sees the row as it was
 * before the edit and hands the caller the stale value.
 *
 * Flushing per statement rather than once at the end also keeps the statement count what it was when
 * these writers issued their own `update` calls, so the timing of the writes has not quietly become
 * a second-order effect of the DAO layer.
 */
internal fun <ID : Any, E : Entity<ID>> E.updateAndFlush(change: E.() -> Unit): E {
    change()
    flush()
    return this
}
