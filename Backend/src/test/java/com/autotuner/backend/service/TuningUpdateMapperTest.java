// Automated test class for TuningUpdateMapperTest behavior; it provides assertions that the related backend behavior remains correct.

package com.autotuner.backend.service;

import com.autotuner.backend.model.TuningUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TuningUpdateMapper.
 */
class TuningUpdateMapperTest {

    private TuningUpdateMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TuningUpdateMapper();
    }

    // ── fromPythonJson ──────────────────────────────────────────────

    @Test
    void fromPythonJson_parsesValidJson() throws Exception {
        String json = "{\"frequency\":82.4,\"note\":\"E2\",\"cents\":-3.5,\"target_frequency\":82.41,\"string_number\":6,\"status\":\"FLAT\"}";

        TuningUpdate update = mapper.fromPythonJson(json);

        assertEquals("tuning_update", update.getType());
        assertEquals("E", update.getString());
        assertEquals("E2", update.getNote());
        assertEquals(82.41, update.getTargetHz(), 0.01);
        assertEquals(82.4, update.getDetectedHz(), 0.01);
        assertEquals(-3.5, update.getCentsOff(), 0.01);
        assertEquals("flat", update.getStatus());
    }

    @Test
    void fromPythonJson_handlesInTuneStatus() throws Exception {
        String json = "{\"frequency\":110.0,\"note\":\"A2\",\"cents\":0.5,\"target_frequency\":110.0,\"string_number\":5,\"status\":\"IN-TUNE\"}";

        TuningUpdate update = mapper.fromPythonJson(json);

        assertEquals("in_tune", update.getStatus());
        assertEquals("A", update.getString());
    }

    @Test
    void fromPythonJson_handlesSharpStatus() throws Exception {
        String json = "{\"frequency\":113.0,\"note\":\"A2\",\"cents\":12.5,\"target_frequency\":110.0,\"string_number\":5,\"status\":\"SHARP\"}";

        TuningUpdate update = mapper.fromPythonJson(json);

        assertEquals("sharp", update.getStatus());
    }

    @Test
    void fromPythonJson_normalizesUnderscoreStatus() throws Exception {
        String json = "{\"frequency\":110.0,\"note\":\"A2\",\"cents\":0.1,\"target_frequency\":110.0,\"string_number\":5,\"status\":\"IN_TUNE\"}";

        TuningUpdate update = mapper.fromPythonJson(json);

        assertEquals("in_tune", update.getStatus());
    }

    @Test
    void fromPythonJson_fallsBackToCentsForUnknownStatus() throws Exception {
        String json = "{\"frequency\":82.4,\"note\":\"E2\",\"cents\":-15.0,\"target_frequency\":82.41,\"string_number\":6,\"status\":\"UNKNOWN\"}";

        TuningUpdate update = mapper.fromPythonJson(json);

        assertEquals("flat", update.getStatus());
    }

    @Test
    void fromPythonJson_handlesUnknownStringNumber() throws Exception {
        String json = "{\"frequency\":82.4,\"note\":\"E2\",\"cents\":0,\"target_frequency\":82.41,\"string_number\":99,\"status\":\"\"}";

        TuningUpdate update = mapper.fromPythonJson(json);

        assertEquals("?", update.getString());
    }

    @Test
    void fromPythonJson_handlesMissingFields() throws Exception {
        String json = "{}";

        TuningUpdate update = mapper.fromPythonJson(json);

        assertEquals("tuning_update", update.getType());
        assertEquals("--", update.getNote());
        assertEquals(0.0, update.getDetectedHz(), 0.01);
        assertEquals("in_tune", update.getStatus()); // 0 cents → in_tune
    }

    @Test
    void fromPythonJson_throwsOnInvalidJson() {
        assertThrows(Exception.class, () -> mapper.fromPythonJson("not json"));
    }

    @Test
    void fromPythonJson_mapsAllStringNumbers() throws Exception {
        String[] expected = {"e", "B", "G", "D", "A", "E"};
        for (int i = 1; i <= 6; i++) {
            String json = String.format(
                    "{\"frequency\":100,\"note\":\"X\",\"cents\":0,\"target_frequency\":100,\"string_number\":%d,\"status\":\"\"}",
                    i
            );
            TuningUpdate update = mapper.fromPythonJson(json);
            assertEquals(expected[i - 1], update.getString(), "String number " + i);
        }
    }

    // ── mockUpdate ──────────────────────────────────────────────────

    @Test
    void mockUpdate_createsValidUpdate() {
        TuningUpdate update = mapper.mockUpdate("A", "A2", 110.0, 109.5, -7.9);

        assertEquals("tuning_update", update.getType());
        assertEquals("A", update.getString());
        assertEquals("A2", update.getNote());
        assertEquals(110.0, update.getTargetHz(), 0.01);
        assertEquals(109.5, update.getDetectedHz(), 0.01);
        assertEquals(-7.9, update.getCentsOff(), 0.01);
        assertEquals("flat", update.getStatus());
        assertTrue(update.getTimestamp() > 0);
    }

    @Test
    void mockUpdate_inTuneWhenCentsWithinDeadzone() {
        TuningUpdate update = mapper.mockUpdate("E", "E2", 82.41, 82.43, 0.4);

        assertEquals("in_tune", update.getStatus());
    }

    @Test
    void mockUpdate_sharpWhenCentsPositive() {
        TuningUpdate update = mapper.mockUpdate("E", "E2", 82.41, 83.0, 12.4);

        assertEquals("sharp", update.getStatus());
    }

    @Test
    void mockUpdate_flatWhenCentsNegative() {
        TuningUpdate update = mapper.mockUpdate("E", "E2", 82.41, 81.0, -29.8);

        assertEquals("flat", update.getStatus());
    }

    @Test
    void mockUpdate_exactBoundary_isInTune() {
        TuningUpdate update = mapper.mockUpdate("E", "E2", 82.41, 82.5, 5.0);

        assertEquals("in_tune", update.getStatus());
    }

    @Test
    void mockUpdate_justOverBoundary_isSharp() {
        TuningUpdate update = mapper.mockUpdate("E", "E2", 82.41, 82.6, 5.1);

        assertEquals("sharp", update.getStatus());
    }
}
