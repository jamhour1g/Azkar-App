package com.azkar.data.repo.exposed

import com.azkar.domain.model.Tag
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.LikeEscapeOp
import org.jetbrains.exposed.v1.core.LowerCase
import org.jetbrains.exposed.v1.core.stringParam

/**
 * Helpers for building safe `LIKE` predicates.
 *
 * `LIKE` treats `%`, `_` and the escape character itself as special. Any user-supplied text
 * used inside a `LIKE` clause must therefore be escaped via [escapeLikePattern] and paired
 * with a predicate that declares `ESCAPE '\'`.
 */
internal object LikeUtils {
    /** The escape character every `LIKE` predicate in this module declares. */
    const val ESCAPE_CHARACTER: Char = '\\'

    /**
     * Escapes the `LIKE` special characters (`\`, `%`, `_`) in [input] so they are matched
     * literally.
     */
    fun String.escapeLikePattern(): String =
        this
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    /** Wraps [input] in `%…%` and escapes it, for a "contains" search. */
    fun String.containsPattern(): String = "%" + escapeLikePattern() + "%"

    /**
     * Case-insensitive "contains" predicate: `lower(column) LIKE lower(:pattern) ESCAPE '\'`.
     *
     * H2's `LIKE` is case-sensitive, so both sides are folded to lower case — the same shape
     * the JPQL queries used. Only for columns whose contents are not already folded, such as
     * remembrance text; see [foldedContains] for the ones that are.
     *
     * The predicate is assembled from [LikeEscapeOp] rather than through the `like()` helper
     * for two reasons: the helper leaves the escape character unset, which silently disables
     * escaping, and its `Expression<T>` receiver does not resolve against a `Column<String>`
     * in 1.5.0. Building the operator directly is the only way to get `ESCAPE '\'` emitted.
     */
    fun Expression<String>.lowerCaseContains(input: String): LikeEscapeOp =
        LikeEscapeOp(
            LowerCase(this),
            stringParam(input.containsPattern().lowercase()),
            true,
            ESCAPE_CHARACTER,
        )

    /**
     * "Contains" predicate against a column that is already stored folded: `column LIKE
     * lower(:pattern) ESCAPE '\'`.
     *
     * Folding the column again would be wasted work and, worse, would throw away any index on it
     * — so a column written through [Tag.fold] is queried directly, and only the pattern is
     * folded.
     */
    fun Expression<String>.foldedContains(input: String): LikeEscapeOp =
        LikeEscapeOp(
            this,
            stringParam(Tag.fold(input).containsPattern()),
            true,
            ESCAPE_CHARACTER,
        )
}
