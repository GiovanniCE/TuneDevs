// DTO describing a complete tuning definition; it provides catalog item serialized by tuning APIs.

package com.autotuner.backend.model;

import java.util.List;

public record TuningDefinitionDto(
    String key,
    String name,
    String instrument,
    boolean custom,
    List<TuningStringDto> strings
) {
}
