package com.futbolix.courts.application.port.in;

import com.futbolix.courts.domain.Court;
import java.util.UUID;

/** FR-001: one court by id; throws CourtNotFoundException when it does not exist. */
public interface GetCourt {
    Court getCourt(UUID courtId);
}
