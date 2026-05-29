// DTO describing one string within a tuning; it provides string metadata used by frontend tuner controls.

package com.autotuner.backend.model;

public record TuningStringDto(
    int id,
    String label,
    String note,
    double freq
) {
}