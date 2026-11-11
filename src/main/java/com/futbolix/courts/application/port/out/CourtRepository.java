package com.futbolix.courts.application.port.out;

import com.futbolix.courts.domain.Court;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourtRepository {

    List<Court> findAll();

    Optional<Court> findById(UUID courtId);

    /** True when another court already uses this name, whatever the case. */
    boolean isNameTakenByAnotherCourt(String name, UUID courtId);

    /** Stores the changes and returns the court as the database left it (updated_at included). */
    Court update(Court court);
}
