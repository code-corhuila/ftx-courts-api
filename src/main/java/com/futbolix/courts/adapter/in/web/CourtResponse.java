package com.futbolix.courts.adapter.in.web;

import com.futbolix.courts.domain.Court;
import java.time.Instant;
import java.util.UUID;

/** Court schema of court-service.yaml. */
public record CourtResponse(UUID id, String name, String sport, int capacity, MoneyDto hourlyPrice,
                            String status, Instant createdAt, Instant updatedAt) {

    public static CourtResponse from(Court court) {
        return new CourtResponse(court.id(), court.name(), court.sport(), court.capacity(),
                new MoneyDto(court.hourlyPrice().amount(), court.hourlyPrice().currency()),
                court.status().name(), court.createdAt(), court.updatedAt());
    }
}
