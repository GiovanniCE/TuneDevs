// Automated test class for TuningCatalogServiceTest behavior; it provides assertions that the related backend behavior remains correct.

package com.autotuner.backend.service;

import com.autotuner.backend.model.CreateCustomTuningRequest;
import com.autotuner.backend.model.TuningDefinitionDto;
import com.autotuner.backend.model.ValidNoteOptionDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TuningCatalogService using an in-memory H2 database.
 */
class TuningCatalogServiceTest {

    private JdbcTemplate jdbcTemplate;
    private TuningCatalogService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:tuningcatalogdb_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(ds);
        service = new TuningCatalogService(jdbcTemplate);
    }

    // The service seeds standard tunings on construction via ensureSchema + seedStandardTunings

    // ── Valid notes ─────────────────────────────────────────────────

    @Test
    void getValidGuitarNotes_returnsNonEmptyList() {
        List<ValidNoteOptionDto> notes = service.getValidGuitarNotes();
        assertFalse(notes.isEmpty());
    }

    @Test
    void getValidGuitarNotes_coversGuitarRange() {
        List<ValidNoteOptionDto> notes = service.getValidGuitarNotes();
        // Current app supports bass + guitar note options from B0 (MIDI 23) to A5 (MIDI 81).
        assertEquals(59, notes.size());
    }

    @Test
    void getValidGuitarNotes_includesStandardTuningNotes() {
        List<ValidNoteOptionDto> notes = service.getValidGuitarNotes();
        List<String> noteNames = notes.stream().map(ValidNoteOptionDto::note).toList();

        assertTrue(noteNames.contains("E2"));
        assertTrue(noteNames.contains("A2"));
        assertTrue(noteNames.contains("D3"));
        assertTrue(noteNames.contains("G3"));
        assertTrue(noteNames.contains("B3"));
        assertTrue(noteNames.contains("E4"));
    }

    @Test
    void getValidGuitarNotes_hasPositiveFrequencies() {
        List<ValidNoteOptionDto> notes = service.getValidGuitarNotes();
        for (ValidNoteOptionDto note : notes) {
            assertTrue(note.freq() > 0, "Frequency for " + note.note() + " should be positive");
        }
    }

    @Test
    void getValidGuitarNotes_a4Is440Hz() {
        List<ValidNoteOptionDto> notes = service.getValidGuitarNotes();
        ValidNoteOptionDto a4 = notes.stream()
            .filter(n -> "A4".equals(n.note()))
            .findFirst()
            .orElse(null);

        assertNotNull(a4);
        assertEquals(440.0, a4.freq(), 0.01);
    }

    // ── Standard tunings ────────────────────────────────────────────

    @Test
    void getStandardTunings_returnsSeededTunings() {
        List<TuningDefinitionDto> tunings = service.getStandardTunings();
        assertFalse(tunings.isEmpty());
    }

    @Test
    void getStandardTunings_includesStandard() {
        List<TuningDefinitionDto> tunings = service.getStandardTunings();
        boolean hasStandard = tunings.stream().anyMatch(t -> "standard".equals(t.key()));
        assertTrue(hasStandard);
    }

    @Test
    void getStandardTunings_includesDropD() {
        List<TuningDefinitionDto> tunings = service.getStandardTunings();
        boolean hasDropD = tunings.stream().anyMatch(t -> "drop_d".equals(t.key()));
        assertTrue(hasDropD);
    }

    @Test
    void getStandardTunings_includesBassStandard() {
        List<TuningDefinitionDto> tunings = service.getStandardTunings();
        boolean hasBassStandard = tunings.stream()
            .anyMatch(t -> "bass_standard".equals(t.key()) && "bass".equals(t.instrument()));
        assertTrue(hasBassStandard);
    }

    @Test
    void getStandardTunings_recoversBassInstrumentForLegacyRows() {
        jdbcTemplate.update(
            "UPDATE tuning_definitions SET instrument = 'guitar' WHERE tuning_key = ?",
            "bass_standard"
        );

        TuningDefinitionDto bassStandard = service.getStandardTunings().stream()
            .filter(t -> "bass_standard".equals(t.key()))
            .findFirst()
            .orElse(null);

        assertNotNull(bassStandard);
        assertEquals("bass", bassStandard.instrument());
    }

    @Test
    void getStandardTunings_allHaveSixStrings() {
        List<TuningDefinitionDto> tunings = service.getStandardTunings();
        for (TuningDefinitionDto tuning : tunings) {
            assertTrue(tuning.strings().size() == 4 || tuning.strings().size() == 6,
                "Tuning " + tuning.key() + " should have 4 or 6 strings");
        }
    }

    @Test
    void getStandardTunings_markedAsNotCustom() {
        List<TuningDefinitionDto> tunings = service.getStandardTunings();
        for (TuningDefinitionDto tuning : tunings) {
            assertFalse(tuning.custom(), tuning.key() + " should not be custom");
        }
    }

    @Test
    void getStandardTunings_standardTuningHasCorrectNotes() {
        List<TuningDefinitionDto> tunings = service.getStandardTunings();
        TuningDefinitionDto standard = tunings.stream()
            .filter(t -> "standard".equals(t.key())).findFirst().orElse(null);

        assertNotNull(standard);
        List<String> notes = standard.strings().stream().map(s -> s.note()).toList();
        assertEquals(List.of("E2", "A2", "D3", "G3", "B3", "E4"), notes);
    }

    // ── User tunings ────────────────────────────────────────────────

    @Test
    void getTuningsForUser_includesStandardAndCustom() {
        // Create a custom tuning first
        service.createCustomTuning("alice", makeStandardRequest("Alice Tuning"));

        List<TuningDefinitionDto> tunings = service.getTuningsForUser("alice");
        // Should have standard + 1 custom
        long standardCount = tunings.stream().filter(t -> !t.custom()).count();
        long customCount = tunings.stream().filter(TuningDefinitionDto::custom).count();

        assertTrue(standardCount > 0, "Should include standard tunings");
        assertEquals(1, customCount, "Should include 1 custom tuning");
    }

    @Test
    void getTuningsForUser_withoutCustom_onlyReturnsStandard() {
        List<TuningDefinitionDto> tunings = service.getTuningsForUser("newuser");

        long standardCount = tunings.stream().filter(t -> !t.custom()).count();
        long customCount = tunings.stream().filter(TuningDefinitionDto::custom).count();

        assertTrue(standardCount > 0);
        assertEquals(0, customCount);
    }

    @Test
    void getTuningForUser_returnsOwnedCustomTuning() {
        TuningDefinitionDto created = service.createCustomTuning("alice", makeStandardRequest("Alice Tuning"));

        TuningDefinitionDto found = service.getTuningForUser("alice", created.key()).orElse(null);

        assertNotNull(found);
        assertEquals(created.key(), found.key());
        assertTrue(found.custom());
    }

    @Test
    void getTuningForUser_returnsStandardTuning() {
        TuningDefinitionDto found = service.getTuningForUser("alice", "standard").orElse(null);

        assertNotNull(found);
        assertEquals("standard", found.key());
        assertFalse(found.custom());
    }

    @Test
    void getTuningForUser_doesNotReturnOtherUsersCustomTuning() {
        TuningDefinitionDto created = service.createCustomTuning("alice", makeStandardRequest("Private"));

        assertTrue(service.getTuningForUser("bob", created.key()).isEmpty());
    }

    // ── Create custom tuning ────────────────────────────────────────

    @Test
    void createCustomTuning_succeeds() {
        TuningDefinitionDto created = service.createCustomTuning("bob", makeStandardRequest("My Tuning"));

        assertNotNull(created);
        assertTrue(created.key().startsWith("bob_"));
        assertEquals("My Tuning", created.name());
        assertEquals("guitar", created.instrument());
        assertTrue(created.custom());
        assertEquals(6, created.strings().size());
    }

    @Test
    void createCustomTuning_supportsBassInstrument() {
        TuningDefinitionDto created = service.createCustomTuning("bob", makeBassRequest("Bass Drop D", "bass"));

        assertEquals("bass", created.instrument());
        assertEquals(4, created.strings().size());
        assertEquals(List.of("D1", "A1", "D2", "G2"), created.strings().stream().map(s -> s.note()).toList());
    }

    @Test
    void createCustomTuning_generatesUniqueKeys() {
        TuningDefinitionDto first = service.createCustomTuning("bob", makeStandardRequest("Test"));
        TuningDefinitionDto second = service.createCustomTuning("bob", makeStandardRequest("Test"));

        assertNotEquals(first.key(), second.key());
    }

    @Test
    void createCustomTuning_sanitizesTuningName() {
        TuningDefinitionDto created = service.createCustomTuning("bob", makeStandardRequest("My !! Tuning **"));

        assertTrue(created.key().contains("my_tuning"), "Key should be sanitized: " + created.key());
    }

    @Test
    void createCustomTuning_rejectsEmptyUsername() {
        assertThrows(IllegalArgumentException.class,
            () -> service.createCustomTuning("", makeStandardRequest("Test")));
    }

    @Test
    void createCustomTuning_rejectsNullUsername() {
        assertThrows(IllegalArgumentException.class,
            () -> service.createCustomTuning(null, makeStandardRequest("Test")));
    }

    @Test
    void createCustomTuning_rejectsNullRequest() {
        assertThrows(IllegalArgumentException.class,
            () -> service.createCustomTuning("bob", null));
    }

    @Test
    void createCustomTuning_rejectsEmptyName() {
        assertThrows(IllegalArgumentException.class,
            () -> service.createCustomTuning("bob", makeStandardRequest("")));
    }

    @Test
    void createCustomTuning_rejectsWrongNumberOfStrings() {
        CreateCustomTuningRequest bad = new CreateCustomTuningRequest("Test", "guitar", List.of(
            new CreateCustomTuningRequest.CustomTuningStringInput(1, "E2"),
            new CreateCustomTuningRequest.CustomTuningStringInput(2, "A2")
        ));

        assertThrows(IllegalArgumentException.class,
            () -> service.createCustomTuning("bob", bad));
    }

    @Test
    void createCustomTuning_rejectsDuplicateStringNumbers() {
        CreateCustomTuningRequest bad = new CreateCustomTuningRequest("Test", "guitar", List.of(
            new CreateCustomTuningRequest.CustomTuningStringInput(1, "E2"),
            new CreateCustomTuningRequest.CustomTuningStringInput(1, "A2"),
            new CreateCustomTuningRequest.CustomTuningStringInput(3, "D3"),
            new CreateCustomTuningRequest.CustomTuningStringInput(4, "G3"),
            new CreateCustomTuningRequest.CustomTuningStringInput(5, "B3"),
            new CreateCustomTuningRequest.CustomTuningStringInput(6, "E4")
        ));

        assertThrows(IllegalArgumentException.class,
            () -> service.createCustomTuning("bob", bad));
    }

    // ── Delete custom tuning ────────────────────────────────────────

    @Test
    void deleteCustomTuning_deletesExisting() {
        TuningDefinitionDto created = service.createCustomTuning("charlie", makeStandardRequest("Del Me"));

        boolean deleted = service.deleteCustomTuning("charlie", created.key());
        assertTrue(deleted);

        // Verify it's gone
        List<TuningDefinitionDto> tunings = service.getTuningsForUser("charlie");
        boolean stillPresent = tunings.stream().anyMatch(t -> t.key().equals(created.key()));
        assertFalse(stillPresent);
    }

    @Test
    void deleteCustomTuning_returnsFalseForNonexistent() {
        boolean deleted = service.deleteCustomTuning("charlie", "fake_tuning_key");
        assertFalse(deleted);
    }

    @Test
    void deleteCustomTuning_cannotDeleteOtherUsersCustomTuning() {
        TuningDefinitionDto created = service.createCustomTuning("alice", makeStandardRequest("Alice Only"));

        boolean deleted = service.deleteCustomTuning("eve", created.key());
        assertFalse(deleted);
    }

    @Test
    void deleteCustomTuning_rejectsStandardTuning() {
        assertThrows(IllegalArgumentException.class,
            () -> service.deleteCustomTuning("alice", "standard"));
    }

    @Test
    void deleteCustomTuning_rejectsEmptyUsername() {
        assertThrows(IllegalArgumentException.class,
            () -> service.deleteCustomTuning("", "some_key"));
    }

    @Test
    void deleteCustomTuning_rejectsEmptyKey() {
        assertThrows(IllegalArgumentException.class,
            () -> service.deleteCustomTuning("alice", ""));
    }

    // ── Helper ──────────────────────────────────────────────────────

    private CreateCustomTuningRequest makeStandardRequest(String name) {
        return new CreateCustomTuningRequest(name, "guitar", List.of(
            new CreateCustomTuningRequest.CustomTuningStringInput(1, "E2"),
            new CreateCustomTuningRequest.CustomTuningStringInput(2, "A2"),
            new CreateCustomTuningRequest.CustomTuningStringInput(3, "D3"),
            new CreateCustomTuningRequest.CustomTuningStringInput(4, "G3"),
            new CreateCustomTuningRequest.CustomTuningStringInput(5, "B3"),
            new CreateCustomTuningRequest.CustomTuningStringInput(6, "E4")
        ));
    }

    private CreateCustomTuningRequest makeBassRequest(String name, String instrument) {
        return new CreateCustomTuningRequest(name, instrument, List.of(
            new CreateCustomTuningRequest.CustomTuningStringInput(1, "D1"),
            new CreateCustomTuningRequest.CustomTuningStringInput(2, "A1"),
            new CreateCustomTuningRequest.CustomTuningStringInput(3, "D2"),
            new CreateCustomTuningRequest.CustomTuningStringInput(4, "G2")
        ));
    }
}
