// Automated test class for HealthControllerTest behavior; it provides assertions that the related backend behavior remains correct.

package com.autotuner.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class HealthControllerTest {

    private final HealthController controller = new HealthController();

    @Test
    void health_returnsOk() {
        ResponseEntity<HealthController.HealthResponse> response = controller.health();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ok", response.getBody().status());
    }

    @Test
    void healthResponse_recordEquality() {
        var a = new HealthController.HealthResponse("ok");
        var b = new HealthController.HealthResponse("ok");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void healthResponse_recordToString() {
        var r = new HealthController.HealthResponse("ok");
        assertTrue(r.toString().contains("ok"));
    }
}
