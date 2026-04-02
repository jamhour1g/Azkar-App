package com.azkar.domain.exception;

/// Thrown when a tag with the requested identifier does not exist.
public class TagNotFoundException extends DataAccessException {

    public TagNotFoundException(long id) {
        super("Tag not found: id=" + id);
    }
}
