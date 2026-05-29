// Automated test class for TuningUpdateMapperTest behavior; it provides assertions that the related backend behavior remains correct.

package com.autotuner.backend;

import com.autotuner.backend.model.TuningUpdate;
import com.autotuner.backend.service.TuningUpdateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TuningUpdateMapperTest {

    private TuningUpdateMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TuningUpdateMapper();
    }

    // --- fromPythonJson field parsing ---

    @Test
    void fromPythonJson_parsesAllFields() throws Exception {
        String json = "{\"frequency\":82.4,\"note\":\"E2\",\"cents\":-5.5,"
                + "\"target_frequency\":82.41,\"string_number\":1,\"status\":\"FLAT\"}";
        TuningUpdate update = mapper.fromPythonJson(json);

        assertThat(update.getType()).isEqualTo("tuning_update");
        assertThat(update.getDetectedHz()).isEqualTo(82.4);
        assertThat(update.getNote()).isEqualTo("E2");
        assertThat(update.getCentsOff()).isEqualTo(-5.5);
        assertThat(update.getTargetHz()).isEqualTo(82.41);
        assertThat(update.getString()).isEqualTo("e");
        assertThat(update.getStatus()).isEqualTo("flat");
    }

    @Test
    void fromPythonJson_setsTimestampToCurrentSecond() throws Exception {
        long before = System.currentTimeMillis() / 1000L;
        String json = "{\"frequency\":82.4,\"note\":\"E2\",\"cents\":0,"
                + "\"target_frequency\":82.41,\"string_number\":1,\"status\":\"IN_TUNE\"}";
        TuningUpdate update = mapper.fromPythonJson(json);
        long after = System.currentTimeMillis() / 1000L;

        assertThat(update.getTimestamp()).isBetween(before, after);
    }

    @Test
    void fromPythonJson_missingFields_usesDefaults() throws Exception {
        TuningUpdate update = mapper.fromPythonJson("{}");

        assertThat(update.getDetectedHz()).isEqualTo(0.0);
        assertThat(update.getNote()).isEqualTo("--");
        assertThat(update.getCentsOff()).isEqualTo(0.0);
        assertThat(update.getTargetHz()).isEqualTo(0.0);
        assertThat(update.getString()).isEqualTo("?");
    }

    @Test
    void fromPythonJson_invalidJson_throwsException() {
        assertThrows(Exception.class, () -> mapper.fromPythonJson("not valid json {{"));
    }

    // --- String number to label mapping ---

    @ParameterizedTest
    @CsvSource({"1,e", "2,B", "3,G", "4,D", "5,A", "6,E"})
    void fromPythonJson_mapsStringNumberToLabel(int stringNumber, String expectedLabel) throws Exception {
        String json = String.format(
                "{\"frequency\":82.4,\"note\":\"E2\",\"cents\":0,"
                        + "\"target_frequency\":82.41,\"string_number\":%d,\"status\":\"IN_TUNE\"}",
                stringNumber);

        assertThat(mapper.fromPythonJson(json).getString()).isEqualTo(expectedLabel);
    }

    @Test
    void fromPythonJson_unknownStringNumber_returnsQuestionMark() throws Exception {
        String json = "{\"frequency\":82.4,\"note\":\"E2\",\"cents\":0,"
                + "\"target_frequency\":82.41,\"string_number\":99,\"status\":\"IN_TUNE\"}";

        assertThat(mapper.fromPythonJson(json).getString()).isEqualTo("?");
    }

    // --- Status normalization: known strings ---

    @Test
    void fromPythonJson_statusInTuneHyphen_normalizesToInTune() throws Exception {
        String json = "{\"frequency\":82.4,\"note\":\"E2\",\"cents\":0,"
                + "\"target_frequency\":82.41,\"string_number\":1,\"status\":\"IN-TUNE\"}";

        assertThat(mapper.fromPythonJson(json).getStatus()).isEqualTo("in_tune");
    }

    @Test
    void fromPythonJson_statusInTuneUnderscore_normalizesToInTune() throws Exception {
        String json = "{\"frequency\":82.4,\"note\":\"E2\",\"cents\":0,"
                + "\"target_frequency\":82.41,\"string_number\":1,\"status\":\"IN_TUNE\"}";

        assertThat(mapper.fromPythonJson(json).getStatus()).isEqualTo("in_tune");
    }

    @Test
    void fromPythonJson_statusFlat_normalizesToFlat() throws Exception {
        String json = "{\"frequency\":82.4,\"note\":\"E2\",\"cents\":-10,"
                + "\"target_frequency\":82.41,\"string_number\":1,\"status\":\"FLAT\"}";

        assertThat(mapper.fromPythonJson(json).getStatus()).isEqualTo("flat");
    }

    @Test
    void fromPythonJson_statusSharp_normalizesToSharp() throws Exception {
        String json = "{\"frequency\":82.4,\"note\":\"E2\",\"cents\":10,"
                + "\"target_frequency\":82.41,\"string_number\":1,\"status\":\"SHARP\"}";

        assertThat(mapper.fromPythonJson(json).getStatus()).isEqualTo("sharp");
    }

    // --- Status normalization: fallback to cents ---

    @Test
    void fromPythonJson_unknownStatus_centsWithinThreshold_returnsInTune() throws Exception {
        String json = "{\"frequency\":82.4,\"note\":\"E2\",\"cents\":3.0,"
                + "\"target_frequency\":82.41,\"string_number\":1,\"status\":\"UNKNOWN\"}";

        assertThat(mapper.fromPythonJson(json).getStatus()).isEqualTo("in_tune");
    }

    @Test
    void fromPythonJson_unknownStatus_centsNegativeOutsideThreshold_returnsFlat() throws Exception {
        String json = "{\"frequency\":82.4,\"note\":\"E2\",\"cents\":-6.0,"
                + "\"target_frequency\":82.41,\"string_number\":1,\"status\":\"\"}";

        assertThat(mapper.fromPythonJson(json).getStatus()).isEqualTo("flat");
    }

    @Test
    void fromPythonJson_unknownStatus_centsPositiveOutsideThreshold_returnsSharp() throws Exception {
        String json = "{\"frequency\":82.4,\"note\":\"E2\",\"cents\":6.0,"
                + "\"target_frequency\":82.41,\"string_number\":1,\"status\":\"\"}";

        assertThat(mapper.fromPythonJson(json).getStatus()).isEqualTo("sharp");
    }

    @Test
    void fromPythonJson_centsExactlyAtPositiveBoundary_returnsInTune() throws Exception {
        String json = "{\"frequency\":82.4,\"note\":\"E2\",\"cents\":5.0,"
                + "\"target_frequency\":82.41,\"string_number\":1,\"status\":\"\"}";

        assertThat(mapper.fromPythonJson(json).getStatus()).isEqualTo("in_tune");
    }

    @Test
    void fromPythonJson_centsExactlyAtNegativeBoundary_returnsInTune() throws Exception {
        String json = "{\"frequency\":82.4,\"note\":\"E2\",\"cents\":-5.0,"
                + "\"target_frequency\":82.41,\"string_number\":1,\"status\":\"\"}";

        assertThat(mapper.fromPythonJson(json).getStatus()).isEqualTo("in_tune");
    }

    // --- mockUpdate ---

    @Test
    void mockUpdate_returnsCorrectFields() {
        TuningUpdate update = mapper.mockUpdate("E", "E2", 82.41, 82.0, -8.0);

        assertThat(update.getType()).isEqualTo("tuning_update");
        assertThat(update.getString()).isEqualTo("E");
        assertThat(update.getNote()).isEqualTo("E2");
        assertThat(update.getTargetHz()).isEqualTo(82.41);
        assertThat(update.getDetectedHz()).isEqualTo(82.0);
        assertThat(update.getCentsOff()).isEqualTo(-8.0);
        assertThat(update.getStatus()).isEqualTo("flat");
    }

    @Test
    void mockUpdate_centsInRange_statusIsInTune() {
        TuningUpdate update = mapper.mockUpdate("A", "A2", 110.0, 110.1, 2.0);
        assertThat(update.getStatus()).isEqualTo("in_tune");
    }

    @Test
    void mockUpdate_centsPositiveOutsideRange_statusIsSharp() {
        TuningUpdate update = mapper.mockUpdate("A", "A2", 110.0, 111.0, 15.0);
        assertThat(update.getStatus()).isEqualTo("sharp");
    }

    @Test
    void mockUpdate_setsTimestamp() {
        long before = System.currentTimeMillis() / 1000L;
        TuningUpdate update = mapper.mockUpdate("E", "E2", 82.41, 82.0, 0.0);
        long after = System.currentTimeMillis() / 1000L;

        assertThat(update.getTimestamp()).isBetween(before, after);
    }
}
