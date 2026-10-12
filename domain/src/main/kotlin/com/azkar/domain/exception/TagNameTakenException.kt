package com.azkar.domain.exception

/**
 * Raised when a tag cannot take [name] because a different live tag already holds it.
 *
 * Not a driver failure, and deliberately not reported as one. `tag.name` carries a unique index, so a
 * rename onto an existing name *is* a constraint violation, and letting it escape would surface as
 * [PersistenceFailureException] — "the storage layer failed", which is both untrue and useless to the
 * person holding the rename dialog. The database caught a rule; the domain is the one that can say
 * which rule.
 *
 * The check is made in `TagService` before the write rather than by interpreting the driver's error
 * afterwards, for the ordinary reason: a conflict worth reporting is one worth not provoking.
 *
 * Carries the name and nothing else. The id of the tag holding it would be the one thing a caller
 * could want, and there is nothing to do with it: the name is unique, so there is no second candidate
 * to disambiguate against, and the sentence a user is shown is "that name is taken" whichever tag is
 * holding it. A field that cannot change what the caller does is not worth the constructor argument it
 * would force `TagService.create` to invent a value for.
 *
 * @property name the folded name that could not be taken
 */
class TagNameTakenException(
    val name: String,
) : DataAccessException("Another tag is already named '$name'")
