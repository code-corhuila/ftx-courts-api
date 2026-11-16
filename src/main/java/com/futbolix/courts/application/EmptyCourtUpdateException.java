package com.futbolix.courts.application;

public class EmptyCourtUpdateException extends RuntimeException {
    public EmptyCourtUpdateException() {
        super("Send at least one field to change");
    }
}
