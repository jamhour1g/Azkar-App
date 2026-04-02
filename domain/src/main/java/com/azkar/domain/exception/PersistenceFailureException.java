package com.azkar.domain.exception;

/// Thrown when a persistence operation (save, delete, query) fails
/// due to an underlying infrastructure error (e.g., constraint violation,
/// connection failure).
///
/// The original JPA/JDBC exception is always available via [#getCause()].
public class PersistenceFailureException extends DataAccessException {

    public PersistenceFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
