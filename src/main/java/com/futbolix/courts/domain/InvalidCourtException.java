package com.futbolix.courts.domain;

/** A court value breaks one of its invariants. {@code field} names the attribute, as in the API. */
public class InvalidCourtException extends RuntimeException {

    private final String field;

    public InvalidCourtException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
