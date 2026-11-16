package com.futbolix.courts.adapter.in.web;

import com.futbolix.courts.application.port.in.GetCourt;
import com.futbolix.courts.application.port.in.ListCourts;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public reads of court-service.yaml (FR-001). */
@RestController
@RequestMapping("/api/v1/courts")
public class CourtController {

    private final ListCourts listCourts;
    private final GetCourt getCourt;

    public CourtController(ListCourts listCourts, GetCourt getCourt) {
        this.listCourts = listCourts;
        this.getCourt = getCourt;
    }

    @GetMapping
    public Map<String, List<CourtResponse>> list() {
        return Map.of("data", listCourts.listCourts().stream().map(CourtResponse::from).toList());
    }

    @GetMapping("/{id}")
    public CourtResponse get(@PathVariable UUID id) {
        return CourtResponse.from(getCourt.getCourt(id));
    }
}
