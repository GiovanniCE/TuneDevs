// Converts raw tuner readings into the shared TuningUpdate schema; it provides normalized TuningUpdate objects for frontend consumption.

package com.autotuner.backend.service;

import com.autotuner.backend.model.TuningUpdate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Converts raw Python tuner output into the frontend's unified tuning_update schema.
 */
@Component
public class TuningUpdateMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Map<Integer, String> STRING_LABELS = Map.of(
            1, "e",
            2, "B",
            3, "G",
            4, "D",
            5, "A",
            6, "E"
    );

    public TuningUpdate fromPythonJson(String rawJsonLine) throws Exception {
        // Hardware mode emits a compact JSON line; map it into the same DTO
        // shape used by browser and mock updates.
        JsonNode node = OBJECT_MAPPER.readTree(rawJsonLine);

        double detectedHz = node.path("frequency").asDouble(0.0);
        String note = node.path("note").asText("--");
        double centsOff = node.path("cents").asDouble(0.0);
        double targetHz = node.path("target_frequency").asDouble(0.0);
        int stringNumber = node.path("string_number").asInt(0);

        String backendStatus = node.path("status").asText("");
        String normalizedStatus = normalizeStatus(backendStatus, centsOff);

        return new TuningUpdate(
                "tuning_update",
                System.currentTimeMillis() / 1000L,
                STRING_LABELS.getOrDefault(stringNumber, "?"),
                note,
                targetHz,
                detectedHz,
                centsOff,
                normalizedStatus
        );
    }

    public TuningUpdate mockUpdate(
            String stringLabel,
            String note,
            double targetHz,
            double detectedHz,
            double centsOff
    ) {
        return new TuningUpdate(
                "tuning_update",
                System.currentTimeMillis() / 1000L,
                stringLabel,
                note,
                targetHz,
                detectedHz,
                centsOff,
                normalizeStatus("", centsOff)
        );
    }

    private String normalizeStatus(String rawStatus, double centsOff) {
        if (rawStatus == null) {
            return fromCents(centsOff);
        }

        // Accept multiple backend spellings, but expose one frontend contract.
        String normalized = rawStatus.trim().toUpperCase();
        return switch (normalized) {
            case "IN-TUNE", "IN_TUNE" -> "in_tune";
            case "FLAT" -> "flat";
            case "SHARP" -> "sharp";
            default -> fromCents(centsOff);
        };
    }

    private String fromCents(double centsOff) {
        // A five-cent deadzone is close enough for the tuner to show in tune.
        if (Math.abs(centsOff) <= 5.0) {
            return "in_tune";
        }
        return centsOff < 0 ? "flat" : "sharp";
    }
}
