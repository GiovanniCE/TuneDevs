// Automated test class for ModelTest behavior; it provides assertions that the related backend behavior remains correct.

package com.autotuner.backend.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelTest {

    // ── TuningUpdate ──────────────────────────────────────

    @Test
    void tuningUpdate_defaultConstructor() {
        TuningUpdate u = new TuningUpdate();
        assertNull(u.getType());
        assertNull(u.getString());
        assertEquals(0, u.getTimestamp());
    }

    @Test
    void tuningUpdate_parameterizedConstructor() {
        TuningUpdate u = new TuningUpdate("tuning_update", 1000L, "A", "A2", 110.0, 109.5, -7.88, "flat");

        assertEquals("tuning_update", u.getType());
        assertEquals(1000L, u.getTimestamp());
        assertEquals("A", u.getString());
        assertEquals("A2", u.getNote());
        assertEquals(110.0, u.getTargetHz());
        assertEquals(109.5, u.getDetectedHz());
        assertEquals(-7.88, u.getCentsOff());
        assertEquals("flat", u.getStatus());
    }

    @Test
    void tuningUpdate_setters() {
        TuningUpdate u = new TuningUpdate();
        u.setType("tuning_update");
        u.setTimestamp(999L);
        u.setString("E");
        u.setNote("E2");
        u.setTargetHz(82.41);
        u.setDetectedHz(82.0);
        u.setCentsOff(-8.63);
        u.setStatus("flat");

        assertEquals("tuning_update", u.getType());
        assertEquals(999L, u.getTimestamp());
        assertEquals("E", u.getString());
        assertEquals("E2", u.getNote());
        assertEquals(82.41, u.getTargetHz());
        assertEquals(82.0, u.getDetectedHz());
        assertEquals(-8.63, u.getCentsOff());
        assertEquals("flat", u.getStatus());
    }

    // ── FavoriteSongPreference ─────────────────────────────

    @Test
    void favoriteSongPreference_defaultConstructor() {
        FavoriteSongPreference f = new FavoriteSongPreference();
        assertNull(f.getName());
        assertNull(f.getTuning());
        assertNull(f.getArtist());
    }

    @Test
    void favoriteSongPreference_twoArgConstructor() {
        FavoriteSongPreference f = new FavoriteSongPreference("Song", "standard");
        assertEquals("Song", f.getName());
        assertEquals("standard", f.getTuning());
        assertNull(f.getArtist());
    }

    @Test
    void favoriteSongPreference_threeArgConstructor() {
        FavoriteSongPreference f = new FavoriteSongPreference("Song", "drop_d", "Artist");
        assertEquals("Song", f.getName());
        assertEquals("drop_d", f.getTuning());
        assertEquals("Artist", f.getArtist());
    }

    @Test
    void favoriteSongPreference_setters() {
        FavoriteSongPreference f = new FavoriteSongPreference();
        f.setName("My Song");
        f.setTuning("eb_standard");
        f.setArtist("My Artist");

        assertEquals("My Song", f.getName());
        assertEquals("eb_standard", f.getTuning());
        assertEquals("My Artist", f.getArtist());
    }

    // ── UserPreferences ─────────────────────────────────────

    @Test
    void userPreferences_defaultConstructor() {
        UserPreferences p = new UserPreferences();
        assertNotNull(p.getPreferredTuningKeys());
        assertTrue(p.getPreferredTuningKeys().isEmpty());
        assertNotNull(p.getFavoriteSongs());
        assertTrue(p.getFavoriteSongs().isEmpty());
    }

    @Test
    void userPreferences_parameterizedConstructor() {
        var keys = List.of("standard", "drop_d");
        var songs = List.of(new FavoriteSongPreference("Song", "standard"));
        UserPreferences p = new UserPreferences(keys, songs);

        assertEquals(2, p.getPreferredTuningKeys().size());
        assertEquals(1, p.getFavoriteSongs().size());
    }

    @Test
    void userPreferences_setters() {
        UserPreferences p = new UserPreferences();
        p.setPreferredTuningKeys(List.of("eb_standard"));
        p.setFavoriteSongs(List.of(new FavoriteSongPreference("Song", "eb_standard", "Artist")));

        assertEquals(1, p.getPreferredTuningKeys().size());
        assertEquals("eb_standard", p.getPreferredTuningKeys().get(0));
        assertEquals("Artist", p.getFavoriteSongs().get(0).getArtist());
    }

    // ── Record DTOs ─────────────────────────────────────────

    @Test
    void tuningStringDto_recordAccessors() {
        TuningStringDto s = new TuningStringDto(1, "E", "E2", 82.41);
        assertEquals(1, s.id());
        assertEquals("E", s.label());
        assertEquals("E2", s.note());
        assertEquals(82.41, s.freq());
    }

    @Test
    void tuningDefinitionDto_recordAccessors() {
        var strings = List.of(new TuningStringDto(1, "E", "E2", 82.41));
        TuningDefinitionDto d = new TuningDefinitionDto("standard", "Standard", "guitar", false, strings);
        assertEquals("standard", d.key());
        assertEquals("Standard", d.name());
        assertEquals("guitar", d.instrument());
        assertFalse(d.custom());
        assertEquals(1, d.strings().size());
    }

    @Test
    void validNoteOptionDto_recordAccessors() {
        ValidNoteOptionDto n = new ValidNoteOptionDto("A2", 110.0);
        assertEquals("A2", n.note());
        assertEquals(110.0, n.freq());
    }

    @Test
    void createCustomTuningRequest_recordAccessors() {
        var input = new CreateCustomTuningRequest.CustomTuningStringInput(1, "E2");
        assertEquals(1, input.stringNumber());
        assertEquals("E2", input.noteName());

        var request = new CreateCustomTuningRequest("Custom", "bass", List.of(input));
        assertEquals("Custom", request.displayName());
        assertEquals("bass", request.instrument());
        assertEquals(1, request.strings().size());
    }

    @Test
    void tuningDefinitionDto_customFlag() {
        var d = new TuningDefinitionDto("custom_a", "Custom A", "guitar", true, List.of());
        assertTrue(d.custom());
    }

    @Test
    void recordEquality() {
        var a = new TuningStringDto(1, "E", "E2", 82.41);
        var b = new TuningStringDto(1, "E", "E2", 82.41);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
