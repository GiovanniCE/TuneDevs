// REST controller for login and registration requests; it provides hTTP success/failure responses for authentication actions.

package com.autotuner.backend.controller;

import com.autotuner.backend.service.UserPreferencesService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class AuthController {

    private final UserPreferencesService userPreferencesService;

    public AuthController(UserPreferencesService userPreferencesService) {
        this.userPreferencesService = userPreferencesService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        if (request.username() == null || request.password() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new AuthResponse(false, "Missing credentials"));
        }

        boolean valid = userPreferencesService.validateLogin(request.username(), request.password());
        if (!valid) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse(false, "Invalid credentials"));
        }

        return ResponseEntity.ok(new AuthResponse(true, "Login successful"));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody AuthRequest request) {
        if (request.username() == null || request.password() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new AuthResponse(false, "Missing credentials"));
        }

        boolean created = userPreferencesService.register(request.username(), request.password());
        if (!created) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new AuthResponse(false, "Username already exists"));
        }

        return ResponseEntity.ok(new AuthResponse(true, "Registration successful"));
    }

    public record AuthRequest(String username, String password) {
    }

    public record AuthResponse(boolean success, String message) {
    }
}
