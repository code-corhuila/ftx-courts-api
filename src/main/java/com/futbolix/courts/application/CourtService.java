package com.futbolix.courts.application;

import com.futbolix.courts.application.port.in.GetCourt;
import com.futbolix.courts.application.port.in.ListCourts;
import com.futbolix.courts.application.port.in.UpdateCourt;
import com.futbolix.courts.application.port.in.UpdateCourtCommand;
import com.futbolix.courts.application.port.out.CourtAuditTrail;
import com.futbolix.courts.application.port.out.CourtRepository;
import com.futbolix.courts.domain.Court;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Use cases of the courts domain. No framework here: wiring and transactions live in the adapters. */
public class CourtService implements ListCourts, GetCourt, UpdateCourt {

    private final CourtRepository courts;
    private final CourtAuditTrail audit;

    public CourtService(CourtRepository courts, CourtAuditTrail audit) {
        this.courts = courts;
        this.audit = audit;
    }

    @Override
    public List<Court> listCourts() {
        return courts.findAll().stream().sorted(Comparator.comparing(Court::name)).toList();
    }

    @Override
    public Court getCourt(UUID courtId) {
        return courts.findById(courtId).orElseThrow(() -> new CourtNotFoundException(courtId));
    }

    @Override
    public Court updateCourt(UpdateCourtCommand command) {
        if (command.isEmpty()) {
            throw new EmptyCourtUpdateException();
        }
        Court court = getCourt(command.courtId());
        Court before = court.snapshot();

        command.name().ifPresent(court::rename);
        command.capacity().ifPresent(court::changeCapacity);
        command.hourlyPrice().ifPresent(court::changeHourlyPrice);
        command.status().ifPresent(court::changeStatus);

        if (command.name().isPresent() && courts.isNameTakenByAnotherCourt(court.name(), court.id())) {
            throw new CourtNameTakenException(court.name());
        }
        Court saved = courts.update(court);
        audit.courtUpdated(command.administratorId(), before, saved);
        return saved;
    }
}
