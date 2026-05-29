// Automated test class for AuthControllerTest behavior; it provides assertions that the related backend behavior remains correct.

package com.autotuner.backend.controller;

import com.autotuner.backend.service.UserPreferencesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    private UserPreferencesService preferencesService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        preferencesService = mock(UserPreferencesService.class);
        controller = new AuthController(preferencesService);
    }

    // ── Login ───────────────────────────────────────────────────────

    @Test
    void login_returnsOkForValidCredentials() {
        when(preferencesService.validateLogin("user", "pass")).thenReturn(true);

        ResponseEntity<AuthController.AuthResponse> response =
            controller.login(new AuthController.AuthRequest("user", "pass"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        assertEquals("Login successful", response.getBody().message());
    }

    @Test
    void login_returns401ForInvalidCredentials() {
        when(preferencesService.validateLogin("user", "wrong")).thenReturn(false);

        ResponseEntity<AuthController.AuthResponse> response =
            controller.login(new AuthController.AuthRequest("user", "wrong"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse(response.getBody().success());
    }

    @Test
    void login_returns400ForMissingUsername() {
        ResponseEntity<AuthController.AuthResponse> response =
            controller.login(new AuthController.AuthRequest(null, "pass"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().success());
    }

    @Test
    void login_returns400ForMissingPassword() {
        ResponseEntity<AuthController.AuthResponse> response =
            controller.login(new AuthController.AuthRequest("user", null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().success());
    }

    // ── Register ────────────────────────────────────────────────────

    @Test
    void register_returnsOkOnSuccess() {
        when(preferencesService.register("newuser", "pass")).thenReturn(true);

        ResponseEntity<AuthController.AuthResponse> response =
            controller.register(new AuthController.AuthRequest("newuser", "pass"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        assertEquals("Registration successful", response.getBody().message());
    }

    @Test
    void register_returns409ForDuplicate() {
        when(preferencesService.register("existing", "pass")).thenReturn(false);

        ResponseEntity<AuthController.AuthResponse> response =
            controller.register(new AuthController.AuthRequest("existing", "pass"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertFalse(response.getBody().success());
    }

    @Test
    void register_returns400ForMissingFields() {
        ResponseEntity<AuthController.AuthResponse> response =
            controller.register(new AuthController.AuthRequest("user", null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
