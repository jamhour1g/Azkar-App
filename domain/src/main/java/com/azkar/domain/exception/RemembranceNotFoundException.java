package com.azkar.domain.exception;

/// Thrown when a remembrance with the requested identifier does not exist.
public class RemembranceNotFoundException extends DataAccessException {

    public RemembranceNotFoundException(long id) {
        super("Remembrance not found: id=" + id);
    }
}
