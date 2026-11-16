package com.futbolix.courts.application;

public class CourtNameTakenException extends RuntimeException {
    public CourtNameTakenException(String name) {
        super("Another court already uses the name " + name);
    }
}
