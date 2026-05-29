// REST controller for standard/custom tuning catalog operations; it provides tuning DTOs, valid note options, or create/delete responses.

package com.autotuner.backend.controller;

import com.autotuner.backend.model.CreateCustomTuningRequest;
import com.autotuner.backend.model.TuningDefinitionDto;
import com.autotuner.backend.model.ValidNoteOptionDto;
import com.autotuner.backend.service.TuningCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/tunings")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class TuningCatalogController {

    private final TuningCatalogService tuningCatalogService;

    public TuningCatalogController(TuningCatalogService tuningCatalogService) {
        this.tuningCatalogService = tuningCatalogService;
    }

    @GetMapping("/notes")
    public ResponseEntity<List<ValidNoteOptionDto>> getValidNotes() {
        // The custom tuning UI uses this list to constrain notes before submit.
        return ResponseEntity.ok(tuningCatalogService.getValidGuitarNotes());
    }

    @GetMapping("/standard")
    public ResponseEntity<List<TuningDefinitionDto>> getStandardTunings() {
        return ResponseEntity.ok(tuningCatalogService.getStandardTunings());
    }

    @GetMapping("/{username}")
    public ResponseEntity<List<TuningDefinitionDto>> getTuningsForUser(@PathVariable String username) {
        // Return standard plus custom tunings in one call for the frontend
        // catalog merge.
        return ResponseEntity.ok(tuningCatalogService.getTuningsForUser(username));
    }

    @GetMapping("/{username}/key/{tuningKey}")
    public ResponseEntity<?> getTuningForUser(
        @PathVariable String username,
        @PathVariable String tuningKey
    ) {
        try {
            Optional<TuningDefinitionDto> tuning = tuningCatalogService.getTuningForUser(username, tuningKey);
            return tuning.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("Tuning not found"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PostMapping("/{username}")
    public ResponseEntity<?> createCustomTuning(
        @PathVariable String username,
        @RequestBody CreateCustomTuningRequest request
    ) {
        try {
            // Service validation keeps controller behavior thin and consistent
            // with future callers.
            return ResponseEntity.status(HttpStatus.CREATED).body(tuningCatalogService.createCustomTuning(username, request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @DeleteMapping("/{username}/key/{tuningKey}")
    public ResponseEntity<?> deleteCustomTuning(
        @PathVariable String username,
        @PathVariable String tuningKey
    ) {
        try {
            boolean deleted = tuningCatalogService.deleteCustomTuning(username, tuningKey);
            if (!deleted) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Custom tuning not found");
            }
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }
}
