package com.futbolix.courts.application.port.in;

import com.futbolix.courts.domain.CourtStatus;
import com.futbolix.courts.domain.Money;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Partial update of a court. Empty fields stay as they are; at least one must be present.
 * {@code administratorId} is the identity the gateway validated (X-User-Id), kept for the audit.
 */
public record UpdateCourtCommand(
        UUID courtId,
        String administratorId,
        Optional<String> name,
        Optional<Integer> capacity,
        Optional<Money> hourlyPrice,
        Optional<CourtStatus> status) {

    public UpdateCourtCommand {
        Objects.requireNonNull(courtId, "courtId");
        Objects.requireNonNull(administratorId, "administratorId");
        name = Objects.requireNonNullElse(name, Optional.empty());
        capacity = Objects.requireNonNullElse(capacity, Optional.empty());
        hourlyPrice = Objects.requireNonNullElse(hourlyPrice, Optional.empty());
        status = Objects.requireNonNullElse(status, Optional.empty());
    }

    public boolean isEmpty() {
        return name.isEmpty() && capacity.isEmpty() && hourlyPrice.isEmpty() && status.isEmpty();
    }
}
