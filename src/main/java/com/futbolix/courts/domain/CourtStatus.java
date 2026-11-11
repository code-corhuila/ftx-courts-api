package com.futbolix.courts.domain;

import java.util.Set;

/**
 * Operational status of a court (02-domain/entities-and-rules.md).
 * ACTIVE can be reserved; INACTIVE and UNDER_MAINTENANCE cannot.
 */
public enum CourtStatus {
    ACTIVE,
    INACTIVE,
    UNDER_MAINTENANCE;

    /** ACTIVE ↔ INACTIVE and ACTIVE ↔ UNDER_MAINTENANCE; staying in the same status is not a transition. */
    public boolean canChangeTo(CourtStatus next) {
        if (next == this) {
            return true;
        }
        return switch (this) {
            case ACTIVE -> Set.of(INACTIVE, UNDER_MAINTENANCE).contains(next);
            case INACTIVE, UNDER_MAINTENANCE -> next == ACTIVE;
        };
    }
}
