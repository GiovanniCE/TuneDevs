// REST controller for loading and saving user tuning/song preferences; it provides preference data or save-status HTTP responses.

package com.autotuner.backend.controller;

import com.autotuner.backend.model.UserPreferences;
import com.autotuner.backend.service.UserPreferencesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/preferences")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class PreferencesController {

    private final UserPreferencesService userPreferencesService;

    public PreferencesController(UserPreferencesService userPreferencesService) {
        this.userPreferencesService = userPreferencesService;
    }

    @GetMapping("/{username}")
    public ResponseEntity<UserPreferences> getPreferences(@PathVariable String username) {
        return ResponseEntity.ok(userPreferencesService.getPreferences(username));
    }

    @PutMapping("/{username}")
    public ResponseEntity<Void> savePreferences(@PathVariable String username, @RequestBody UserPreferences preferences) {
        userPreferencesService.savePreferences(username, preferences);
        return ResponseEntity.noContent().build();
    }
}
