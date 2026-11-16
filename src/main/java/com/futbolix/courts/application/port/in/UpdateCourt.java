package com.futbolix.courts.application.port.in;

import com.futbolix.courts.domain.Court;

/** FR-028, FR-030, FR-031: an administrator changes name, capacity, price or status. */
public interface UpdateCourt {
    Court updateCourt(UpdateCourtCommand command);
}
