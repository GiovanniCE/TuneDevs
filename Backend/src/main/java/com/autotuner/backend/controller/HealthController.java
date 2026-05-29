// Simple REST health endpoint for smoke tests and uptime checks; it provides response indicating the backend is reachable.

package com.autotuner.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal health endpoint for local development, deployment checks,
 * and simple HTTP-based testing.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("ok"));
    }

    public record HealthResponse(String status) {}
}