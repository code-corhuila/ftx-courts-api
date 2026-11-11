package com.futbolix.courts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CourtTest {

    private static Court court(CourtStatus status) {
        Instant now = Instant.parse("2026-11-10T12:00:00Z");
        return new Court(UUID.randomUUID(), "Cancha 1", Court.FOOTBALL, 10, Money.cop(60000), status, now, now);
    }

    @ParameterizedTest(name = "{0} -> {1} allowed")
    @CsvSource({
        "ACTIVE, INACTIVE",
        "ACTIVE, UNDER_MAINTENANCE",
        "INACTIVE, ACTIVE",
        "UNDER_MAINTENANCE, ACTIVE",
        "ACTIVE, ACTIVE"
    })
    void allowedTransitions(CourtStatus from, CourtStatus to) {
        Court court = court(from);
        court.changeStatus(to);
        assertThat(court.status()).isEqualTo(to);
    }

    @ParameterizedTest(name = "{0} -> {1} rejected")
    @CsvSource({
        "INACTIVE, UNDER_MAINTENANCE",
        "UNDER_MAINTENANCE, INACTIVE"
    })
    void forbiddenTransitions(CourtStatus from, CourtStatus to) {
        Court court = court(from);
        assertThatThrownBy(() -> court.changeStatus(to))
                .isInstanceOf(InvalidCourtStatusTransitionException.class);
        assertThat(court.status()).isEqualTo(from);
    }

    @Test
    void nameIsTrimmedAndBounded() {
        Court court = court(CourtStatus.ACTIVE);
        court.rename("  Cancha Norte  ");
        assertThat(court.name()).isEqualTo("Cancha Norte");

        assertThatThrownBy(() -> court.rename("   "))
                .isInstanceOf(InvalidCourtException.class)
                .extracting(e -> ((InvalidCourtException) e).field()).isEqualTo("name");
        assertThatThrownBy(() -> court.rename("x".repeat(101)))
                .isInstanceOf(InvalidCourtException.class);
    }

    @Test
    void capacityMustBePositive() {
        Court court = court(CourtStatus.ACTIVE);
        assertThatThrownBy(() -> court.changeCapacity(0))
                .isInstanceOf(InvalidCourtException.class)
                .extracting(e -> ((InvalidCourtException) e).field()).isEqualTo("capacity");
    }

    @Test
    void moneyIsWholePositiveCop() {
        assertThat(Money.cop(70000).amount()).isEqualTo(70000);
        assertThatThrownBy(() -> Money.cop(0)).isInstanceOf(InvalidCourtException.class);
        assertThatThrownBy(() -> new Money(70000, "USD")).isInstanceOf(InvalidCourtException.class);
    }

    @Test
    void snapshotIsIndependent() {
        Court court = court(CourtStatus.ACTIVE);
        Court before = court.snapshot();
        court.changeHourlyPrice(Money.cop(90000));
        assertThat(before.hourlyPrice().amount()).isEqualTo(60000);
    }
}
