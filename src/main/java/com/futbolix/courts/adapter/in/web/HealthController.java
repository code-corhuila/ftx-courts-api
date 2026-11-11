package com.futbolix.courts.adapter.in.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/v1/health with the body of court-service.yaml (status "ok", timestamp, dependencies).
 * The container probes keep using the actuator endpoints /health/liveness and /health/readiness.
 */
@RestController
public class HealthController {

    private final JdbcClient jdbc;

    public HealthController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/v1/health")
    ResponseEntity<?> health() {
        try {
            jdbc.sql("SELECT 1").query(Integer.class).single();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse("SERVICE_UNAVAILABLE", "The service is temporarily unavailable", null, null));
        }
        return ResponseEntity.ok(new Health("ok", Instant.now(), Map.of("database", "ok")));
    }

    record Health(String status, Instant timestamp, Map<String, String> dependencies) {
    }
}
