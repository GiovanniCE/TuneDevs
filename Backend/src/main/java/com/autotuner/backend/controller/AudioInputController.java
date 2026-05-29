// STOMP controller that receives browser-detected audio frequencies; it provides normalized tuning_update messages broadcast to /topic/tuning.

package com.autotuner.backend.controller;

import com.autotuner.backend.model.TuningUpdate;
import com.autotuner.backend.service.TuningUpdateMapper;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

/**
 * Receives detected frequency from the browser (Web Audio API pitch detection)
 * and broadcasts a normalized TuningUpdate on /topic/tuning — same flow as
 * the Python hardware/mock modes.
 */
@Controller
public class AudioInputController {

    private static final List<StringSpec> STANDARD_STRINGS = List.of(
            new StringSpec(1, "E", "E2", 82.41),
            new StringSpec(2, "A", "A2", 110.00),
            new StringSpec(3, "D", "D3", 146.83),
            new StringSpec(4, "G", "G3", 196.00),
            new StringSpec(5, "B", "B3", 246.94),
            new StringSpec(6, "e", "E4", 329.63)
    );

    private final SimpMessagingTemplate messagingTemplate;
    private final TuningUpdateMapper tuningUpdateMapper;

    public AudioInputController(
            SimpMessagingTemplate messagingTemplate,
            TuningUpdateMapper tuningUpdateMapper
    ) {
        this.messagingTemplate = messagingTemplate;
        this.tuningUpdateMapper = tuningUpdateMapper;
    }

    /**
     * Browser sends: { "detectedHz": 109.5, "targetHz": 110.0 }
     * If targetHz is provided, we use it directly; otherwise we find the closest standard string.
     */
    @MessageMapping("/audio-frequency")
    public void handleAudioFrequency(Map<String, Object> payload) {
        double detectedHz = toDouble(payload.get("detectedHz"));
        if (detectedHz <= 0) return;

        double targetHz = toDouble(payload.get("targetHz"));
        // Prefer the frontend-selected target so alternate/custom tunings are
        // respected. Fall back to standard strings for older clients.
        StringSpec match = readSelectedString(payload, targetHz);
        if (match == null) {
            match = findClosestString(targetHz > 0 ? targetHz : detectedHz);
        }

        double centsOff = 1200.0 * Math.log(detectedHz / match.targetHz()) / Math.log(2.0);
        centsOff = Math.round(centsOff * 100.0) / 100.0;

        TuningUpdate update = tuningUpdateMapper.mockUpdate(
                match.label(),
                match.note(),
                match.targetHz(),
                Math.round(detectedHz * 100.0) / 100.0,
                centsOff
        );

        messagingTemplate.convertAndSend("/topic/tuning", update);
    }

    private StringSpec readSelectedString(Map<String, Object> payload, double targetHz) {
        if (targetHz <= 0) {
            return null;
        }

        String label = readString(payload.get("stringLabel"));
        String note = readString(payload.get("note"));
        int stringNumber = toInt(payload.get("stringNumber"));

        if (label == null || note == null) {
            return null;
        }

        // The frontend already knows the active tuning, so this can represent
        // custom or bass strings as long as targetHz is provided.
        return new StringSpec(stringNumber, label, note, targetHz);
    }

    private StringSpec findClosestString(double hz) {
        StringSpec closest = STANDARD_STRINGS.get(0);
        double minDistance = Double.MAX_VALUE;

        for (StringSpec spec : STANDARD_STRINGS) {
            // Compare in cents so low and high strings are treated musically.
            double distance = Math.abs(1200.0 * Math.log(hz / spec.targetHz()) / Math.log(2.0));
            if (distance < minDistance) {
                minDistance = distance;
                closest = spec;
            }
        }

        return closest;
    }

    private int toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    private String readString(Object value) {
        if (value instanceof String s) {
            String trimmed = s.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        return null;
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    private record StringSpec(int id, String label, String note, double targetHz) {}
}
