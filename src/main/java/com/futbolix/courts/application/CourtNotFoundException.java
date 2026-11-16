package com.futbolix.courts.application;

import java.util.UUID;

public class CourtNotFoundException extends RuntimeException {
    public CourtNotFoundException(UUID courtId) {
        super("Court " + courtId + " does not exist");
    }
}
