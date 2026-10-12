package com.azkar.domain.exception

/**
 * Raised when an operation targets a remembrance that does not exist.
 *
 * @property remembranceId the identifier that was looked up
 */
class RemembranceNotFoundException(
    val remembranceId: Long,
) : DataAccessException("Remembrance $remembranceId was not found")
