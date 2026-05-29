// DTO representing a request to create a custom tuning; it provides structured data consumed by TuningCatalogService validation.

package com.autotuner.backend.model;

import java.util.List;

public record CreateCustomTuningRequest(
    String displayName,
    String instrument,
    List<CustomTuningStringInput> strings
) {
    public record CustomTuningStringInput(
        int stringNumber,
        String noteName
    ) {
    }
}
