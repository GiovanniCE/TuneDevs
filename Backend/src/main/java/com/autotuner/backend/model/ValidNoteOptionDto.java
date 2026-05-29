// DTO for a selectable note and its calculated frequency; it provides valid-note option used by the custom tuning UI.

package com.autotuner.backend.model;

public record ValidNoteOptionDto(
    String note,
    double freq
) {
}