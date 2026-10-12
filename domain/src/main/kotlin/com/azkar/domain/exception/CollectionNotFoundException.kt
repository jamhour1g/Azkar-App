package com.azkar.domain.exception

/**
 * Raised when an operation targets a collection that does not exist.
 *
 * @property collectionId the identifier that was looked up
 */
class CollectionNotFoundException(
    val collectionId: Long,
) : DataAccessException("Collection $collectionId was not found")
