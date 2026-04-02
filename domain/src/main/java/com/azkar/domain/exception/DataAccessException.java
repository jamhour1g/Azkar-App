package com.azkar.domain.exception;

/// Base exception for all data-access failures in the domain layer.
///
/// This acts as the domain's umbrella for persistence-related errors,
/// keeping JPA/JDBC specifics out of the domain API. All other domain
/// data exceptions extend this class.
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
