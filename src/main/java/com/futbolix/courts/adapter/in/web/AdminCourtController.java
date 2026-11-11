package com.futbolix.courts.adapter.in.web;

import com.futbolix.courts.application.port.in.UpdateCourt;
import com.futbolix.courts.application.port.in.UpdateCourtCommand;
import com.futbolix.courts.domain.Money;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PATCH /admin/courts/{id} of court-service.yaml (FR-028, FR-030, FR-031). */
@RestController
@RequestMapping("/api/v1/admin/courts")
public class AdminCourtController {

    private final UpdateCourt updateCourt;

    public AdminCourtController(UpdateCourt updateCourt) {
        this.updateCourt = updateCourt;
    }

    @PatchMapping("/{id}")
    public CourtResponse update(@PathVariable UUID id,
                                @RequestBody UpdateCourtRequest body,
                                @RequestAttribute(AdministratorInterceptor.ADMINISTRATOR_ID_ATTRIBUTE) String administratorId) {
        Optional<Money> price = Optional.ofNullable(body.hourlyPrice())
                .map(p -> new Money(p.amount() == null ? 0 : p.amount(), p.currency()));
        UpdateCourtCommand command = new UpdateCourtCommand(id, administratorId,
                Optional.ofNullable(body.name()), Optional.ofNullable(body.capacity()), price,
                Optional.ofNullable(body.status()));
        return CourtResponse.from(updateCourt.updateCourt(command));
    }
}
