package com.futbolix.courts.adapter.in.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.futbolix.courts.application.CourtNotFoundException;
import com.futbolix.courts.application.port.in.GetCourt;
import com.futbolix.courts.application.port.in.ListCourts;
import com.futbolix.courts.application.port.in.UpdateCourt;
import com.futbolix.courts.domain.Court;
import com.futbolix.courts.domain.CourtStatus;
import com.futbolix.courts.domain.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {CourtController.class, AdminCourtController.class})
@Import({ApiExceptionHandler.class, CorrelationIdFilter.class, AdministratorInterceptor.class, WebConfiguration.class,
        TestVerifierConfiguration.class})
class CourtControllerTest {

    static final UUID COURT_1 = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Autowired MockMvc mvc;
    @MockitoBean ListCourts listCourts;
    @MockitoBean GetCourt getCourt;
    @MockitoBean UpdateCourt updateCourt;

    static Court court() {
        Instant t = Instant.parse("2026-11-10T12:00:00Z");
        return new Court(COURT_1, "Cancha 1", Court.FOOTBALL, 10, Money.cop(60000), CourtStatus.ACTIVE, t, t);
    }

    @Test
    void listsCourtsWithTheContractShape() throws Exception {
        when(listCourts.listCourts()).thenReturn(List.of(court()));

        mvc.perform(get("/api/v1/courts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(COURT_1.toString()))
                .andExpect(jsonPath("$.data[0].name").value("Cancha 1"))
                .andExpect(jsonPath("$.data[0].sport").value("FOOTBALL"))
                .andExpect(jsonPath("$.data[0].capacity").value(10))
                .andExpect(jsonPath("$.data[0].hourlyPrice.amount").value(60000))
                .andExpect(jsonPath("$.data[0].hourlyPrice.currency").value("COP"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-11-10T12:00:00Z"));
    }

    @Test
    void anUnknownCourtIs404WithTheCallersTraceId() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(getCourt.getCourt(unknown)).thenThrow(new CourtNotFoundException(unknown));

        mvc.perform(get("/api/v1/courts/{id}", unknown).header("X-Correlation-Id", "trace-123"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Correlation-Id", "trace-123"))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value("trace-123"));
    }

    @Test
    void aMalformedIdIs400() throws Exception {
        mvc.perform(get("/api/v1/courts/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("id"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
