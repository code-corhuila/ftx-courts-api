package com.futbolix.courts.adapter.in.web;

import com.futbolix.courts.domain.CourtStatus;

/**
 * UpdateCourtRequest of court-service.yaml. A missing field is not changed; unknown fields are
 * rejected (spring.jackson.deserialization.fail-on-unknown-properties). The values are checked by
 * the domain, so the rules live in one place.
 */
public record UpdateCourtRequest(String name, Integer capacity, MoneyDto hourlyPrice, CourtStatus status) {
}
