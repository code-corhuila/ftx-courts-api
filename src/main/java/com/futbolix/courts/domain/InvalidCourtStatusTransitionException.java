package com.futbolix.courts.domain;

/** The requested status change is not one of the allowed transitions (422 in the API). */
public class InvalidCourtStatusTransitionException extends RuntimeException {

    private final CourtStatus from;
    private final CourtStatus to;

    public InvalidCourtStatusTransitionException(CourtStatus from, CourtStatus to) {
        super("A court cannot change from " + from + " to " + to);
        this.from = from;
        this.to = to;
    }

    public CourtStatus from() {
        return from;
    }

    public CourtStatus to() {
        return to;
    }
}
