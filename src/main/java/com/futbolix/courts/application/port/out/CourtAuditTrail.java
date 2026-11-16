package com.futbolix.courts.application.port.out;

import com.futbolix.courts.domain.Court;

/** FR-036: every administrative change is recorded with who made it and what changed. */
public interface CourtAuditTrail {
    void courtUpdated(String administratorId, Court before, Court after);
}
