// Automated test class for TuningUpdateTest behavior; it provides assertions that the related backend behavior remains correct.

package com.autotuner.backend;

import com.autotuner.backend.model.TuningUpdate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TuningUpdateTest {

    @Test
    void defaultConstructor_createsInstance() {
        assertThat(new TuningUpdate()).isNotNull();
    }

    @Test
    void fullConstructor_setsAllFields() {
        TuningUpdate update = new TuningUpdate("tuning_update", 1000L, "E", "E2", 82.41, 82.0, -8.5, "flat");

        assertThat(update.getType()).isEqualTo("tuning_update");
        assertThat(update.getTimestamp()).isEqualTo(1000L);
        assertThat(update.getString()).isEqualTo("E");
        assertThat(update.getNote()).isEqualTo("E2");
        assertThat(update.getTargetHz()).isEqualTo(82.41);
        assertThat(update.getDetectedHz()).isEqualTo(82.0);
        assertThat(update.getCentsOff()).isEqualTo(-8.5);
        assertThat(update.getStatus()).isEqualTo("flat");
    }

    @Test
    void setType_updatesValue() {
        TuningUpdate update = new TuningUpdate();
        update.setType("new_type");
        assertThat(update.getType()).isEqualTo("new_type");
    }

    @Test
    void setTimestamp_updatesValue() {
        TuningUpdate update = new TuningUpdate();
        update.setTimestamp(9999L);
        assertThat(update.getTimestamp()).isEqualTo(9999L);
    }

    @Test
    void setString_updatesValue() {
        TuningUpdate update = new TuningUpdate();
        update.setString("A");
        assertThat(update.getString()).isEqualTo("A");
    }

    @Test
    void setNote_updatesValue() {
        TuningUpdate update = new TuningUpdate();
        update.setNote("A2");
        assertThat(update.getNote()).isEqualTo("A2");
    }

    @Test
    void setTargetHz_updatesValue() {
        TuningUpdate update = new TuningUpdate();
        update.setTargetHz(110.0);
        assertThat(update.getTargetHz()).isEqualTo(110.0);
    }

    @Test
    void setDetectedHz_updatesValue() {
        TuningUpdate update = new TuningUpdate();
        update.setDetectedHz(109.5);
        assertThat(update.getDetectedHz()).isEqualTo(109.5);
    }

    @Test
    void setCentsOff_updatesValue() {
        TuningUpdate update = new TuningUpdate();
        update.setCentsOff(-7.8);
        assertThat(update.getCentsOff()).isEqualTo(-7.8);
    }

    @Test
    void setStatus_updatesValue() {
        TuningUpdate update = new TuningUpdate();
        update.setStatus("sharp");
        assertThat(update.getStatus()).isEqualTo("sharp");
    }
}
