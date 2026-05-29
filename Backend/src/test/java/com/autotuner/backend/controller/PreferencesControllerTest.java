// Automated test class for PreferencesControllerTest behavior; it provides assertions that the related backend behavior remains correct.

package com.autotuner.backend.controller;

import com.autotuner.backend.model.UserPreferences;
import com.autotuner.backend.service.UserPreferencesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PreferencesControllerTest {

    private UserPreferencesService preferencesService;
    private PreferencesController controller;

    @BeforeEach
    void setUp() {
        preferencesService = mock(UserPreferencesService.class);
        controller = new PreferencesController(preferencesService);
    }

    @Test
    void getPreferences_returnsOk() {
        UserPreferences prefs = new UserPreferences(List.of("standard"), List.of());
        when(preferencesService.getPreferences("testuser")).thenReturn(prefs);

        ResponseEntity<UserPreferences> response = controller.getPreferences("testuser");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("standard", response.getBody().getPreferredTuningKeys().get(0));
    }

    @Test
    void getPreferences_returnsEmptyForUnknown() {
        UserPreferences empty = new UserPreferences(List.of(), List.of());
        when(preferencesService.getPreferences("ghost")).thenReturn(empty);

        ResponseEntity<UserPreferences> response = controller.getPreferences("ghost");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getPreferredTuningKeys().isEmpty());
        assertTrue(response.getBody().getFavoriteSongs().isEmpty());
    }

    @Test
    void savePreferences_returnsNoContent() {
        UserPreferences prefs = new UserPreferences(List.of("drop_d"), List.of());

        ResponseEntity<Void> response = controller.savePreferences("testuser", prefs);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(preferencesService).savePreferences(eq("testuser"), any(UserPreferences.class));
    }
}
