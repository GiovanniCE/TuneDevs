// Automated test class for TuningCatalogControllerTest behavior; it provides assertions that the related backend behavior remains correct.

package com.autotuner.backend.controller;

import com.autotuner.backend.model.CreateCustomTuningRequest;
import com.autotuner.backend.model.TuningDefinitionDto;
import com.autotuner.backend.model.TuningStringDto;
import com.autotuner.backend.model.ValidNoteOptionDto;
import com.autotuner.backend.service.TuningCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TuningCatalogControllerTest {

    private TuningCatalogService service;
    private TuningCatalogController controller;

    @BeforeEach
    void setUp() {
        service = mock(TuningCatalogService.class);
        controller = new TuningCatalogController(service);
    }

    @Test
    void getValidNotes_returnsNotes() {
        var notes = List.of(new ValidNoteOptionDto("E2", 82.41), new ValidNoteOptionDto("A2", 110.0));
        when(service.getValidGuitarNotes()).thenReturn(notes);

        ResponseEntity<List<ValidNoteOptionDto>> response = controller.getValidNotes();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        assertEquals("E2", response.getBody().get(0).note());
    }

    @Test
    void getStandardTunings_returnsTunings() {
        var strings = List.of(new TuningStringDto(1, "E", "E2", 82.41));
        var tunings = List.of(new TuningDefinitionDto("standard", "Standard", "guitar", false, strings));
        when(service.getStandardTunings()).thenReturn(tunings);

        ResponseEntity<List<TuningDefinitionDto>> response = controller.getStandardTunings();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("standard", response.getBody().get(0).key());
    }

    @Test
    void getTuningsForUser_delegatesToService() {
        var tunings = List.<TuningDefinitionDto>of();
        when(service.getTuningsForUser("alice")).thenReturn(tunings);

        ResponseEntity<List<TuningDefinitionDto>> response = controller.getTuningsForUser("alice");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service).getTuningsForUser("alice");
    }

    @Test
    void getTuningForUser_returnsTuning() {
        var tuning = new TuningDefinitionDto(
            "alice_dadgad",
            "DADGAD",
            "guitar",
            true,
            List.of(new TuningStringDto(1, "D2", "D2", 73.42))
        );
        when(service.getTuningForUser("alice", "alice_dadgad")).thenReturn(Optional.of(tuning));

        ResponseEntity<?> response = controller.getTuningForUser("alice", "alice_dadgad");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(tuning, response.getBody());
    }

    @Test
    void getTuningForUser_returns404WhenMissing() {
        when(service.getTuningForUser("alice", "missing")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getTuningForUser("alice", "missing");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void createCustomTuning_returns201() {
        var request = new CreateCustomTuningRequest("My Tuning", "guitar", List.of(
            new CreateCustomTuningRequest.CustomTuningStringInput(1, "E2")
        ));
        var result = new TuningDefinitionDto("custom_my_tuning", "My Tuning", "guitar", true,
            List.of(new TuningStringDto(1, "E", "E2", 82.41)));
        when(service.createCustomTuning("bob", request)).thenReturn(result);

        ResponseEntity<?> response = controller.createCustomTuning("bob", request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(result, response.getBody());
    }

    @Test
    void createCustomTuning_returnsBadRequestOnIllegalArgument() {
        var request = new CreateCustomTuningRequest("", "guitar", List.of());
        when(service.createCustomTuning("bob", request)).thenThrow(new IllegalArgumentException("Name required"));

        ResponseEntity<?> response = controller.createCustomTuning("bob", request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Name required", response.getBody());
    }

    @Test
    void deleteCustomTuning_returns204() {
        when(service.deleteCustomTuning("bob", "custom_key")).thenReturn(true);

        ResponseEntity<?> response = controller.deleteCustomTuning("bob", "custom_key");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void deleteCustomTuning_returns404WhenNotFound() {
        when(service.deleteCustomTuning("bob", "missing")).thenReturn(false);

        ResponseEntity<?> response = controller.deleteCustomTuning("bob", "missing");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void deleteCustomTuning_returnsBadRequestOnIllegalArgument() {
        when(service.deleteCustomTuning("bob", "standard")).thenThrow(new IllegalArgumentException("Cannot delete standard"));

        ResponseEntity<?> response = controller.deleteCustomTuning("bob", "standard");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
