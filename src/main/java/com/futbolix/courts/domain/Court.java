package com.futbolix.courts.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One of the four fixed football courts. Courts are seeded by ftx-courts-db and never created
 * or deleted (BR-001): this aggregate only knows how to change itself.
 */
public final class Court {

    public static final String FOOTBALL = "FOOTBALL";
    static final int MAX_NAME_LENGTH = 100;

    private final UUID id;
    private final String sport;
    private final Instant createdAt;
    private String name;
    private int capacity;
    private Money hourlyPrice;
    private CourtStatus status;
    private Instant updatedAt;

    public Court(UUID id, String name, String sport, int capacity, Money hourlyPrice,
                 CourtStatus status, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.sport = Objects.requireNonNull(sport, "sport");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.name = validName(name);
        this.capacity = validCapacity(capacity);
        this.hourlyPrice = Objects.requireNonNull(hourlyPrice, "hourlyPrice");
        this.status = Objects.requireNonNull(status, "status");
    }

    /** An independent copy, so a change can be compared with the state before it. */
    public Court snapshot() {
        return new Court(id, name, sport, capacity, hourlyPrice, status, createdAt, updatedAt);
    }

    public void rename(String newName) {
        this.name = validName(newName);
    }

    public void changeCapacity(int newCapacity) {
        this.capacity = validCapacity(newCapacity);
    }

    public void changeHourlyPrice(Money newPrice) {
        this.hourlyPrice = Objects.requireNonNull(newPrice, "hourlyPrice");
    }

    public void changeStatus(CourtStatus next) {
        Objects.requireNonNull(next, "status");
        if (!status.canChangeTo(next)) {
            throw new InvalidCourtStatusTransitionException(status, next);
        }
        this.status = next;
    }

    private static String validName(String candidate) {
        String trimmed = candidate == null ? "" : candidate.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidCourtException("name", "The name must have between 1 and 100 characters");
        }
        return trimmed;
    }

    private static int validCapacity(int candidate) {
        if (candidate < 1) {
            throw new InvalidCourtException("capacity", "The capacity must be greater than 0");
        }
        return candidate;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String sport() {
        return sport;
    }

    public int capacity() {
        return capacity;
    }

    public Money hourlyPrice() {
        return hourlyPrice;
    }

    public CourtStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
