// Automated test class for AudioInputControllerTest behavior; it provides assertions that the related backend behavior remains correct.

package com.autotuner.backend.controller;

import com.autotuner.backend.model.TuningUpdate;
import com.autotuner.backend.service.TuningUpdateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AudioInputControllerTest {

    private SimpMessagingTemplate messagingTemplate;
    private TuningUpdateMapper tuningUpdateMapper;
    private AudioInputController controller;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        tuningUpdateMapper = new TuningUpdateMapper();
        controller = new AudioInputController(messagingTemplate, tuningUpdateMapper);
    }

    @Test
    void handleAudioFrequency_sendsUpdateToTopic() {
        controller.handleAudioFrequency(Map.of("detectedHz", 110.0, "targetHz", 110.0));

        ArgumentCaptor<TuningUpdate> captor = ArgumentCaptor.forClass(TuningUpdate.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/tuning"), captor.capture());

        TuningUpdate update = captor.getValue();
        assertEquals("tuning_update", update.getType());
        assertEquals(110.0, update.getDetectedHz());
        assertEquals("in_tune", update.getStatus());
    }

    @Test
    void handleAudioFrequency_computesCentsOff() {
        // 112 Hz vs 110 Hz target → should be slightly sharp
        controller.handleAudioFrequency(Map.of("detectedHz", 112.0, "targetHz", 110.0));

        ArgumentCaptor<TuningUpdate> captor = ArgumentCaptor.forClass(TuningUpdate.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/tuning"), captor.capture());

        TuningUpdate update = captor.getValue();
        assertTrue(update.getCentsOff() > 0, "Should be sharp");
        assertEquals("sharp", update.getStatus());
    }

    @Test
    void handleAudioFrequency_flatDetection() {
        // 107 Hz vs 110 Hz target → should be flat
        controller.handleAudioFrequency(Map.of("detectedHz", 107.0, "targetHz", 110.0));

        ArgumentCaptor<TuningUpdate> captor = ArgumentCaptor.forClass(TuningUpdate.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/tuning"), captor.capture());

        TuningUpdate update = captor.getValue();
        assertTrue(update.getCentsOff() < 0, "Should be flat");
        assertEquals("flat", update.getStatus());
    }

    @Test
    void handleAudioFrequency_findsClosestStringWhenNoTarget() {
        // 82 Hz is close to E2 (82.41)
        controller.handleAudioFrequency(Map.of("detectedHz", 82.0));

        ArgumentCaptor<TuningUpdate> captor = ArgumentCaptor.forClass(TuningUpdate.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/tuning"), captor.capture());

        TuningUpdate update = captor.getValue();
        assertEquals("E", update.getString());
        assertEquals("E2", update.getNote());
    }

    @Test
    void handleAudioFrequency_closestStringForHighFrequency() {
        // 330 Hz is close to E4 (329.63)
        controller.handleAudioFrequency(Map.of("detectedHz", 330.0));

        ArgumentCaptor<TuningUpdate> captor = ArgumentCaptor.forClass(TuningUpdate.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/tuning"), captor.capture());

        TuningUpdate update = captor.getValue();
        assertEquals("e", update.getString());
        assertEquals("E4", update.getNote());
    }

    @Test
    void handleAudioFrequency_ignoresZeroHz() {
        controller.handleAudioFrequency(Map.of("detectedHz", 0.0));
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void handleAudioFrequency_ignoresNegativeHz() {
        controller.handleAudioFrequency(Map.of("detectedHz", -50.0));
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void handleAudioFrequency_handlesMissingDetectedHz() {
        controller.handleAudioFrequency(Map.of("targetHz", 110.0));
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void handleAudioFrequency_parsesStringValues() {
        controller.handleAudioFrequency(Map.of("detectedHz", "110.0", "targetHz", "110.0"));

        verify(messagingTemplate).convertAndSend(eq("/topic/tuning"), any(TuningUpdate.class));
    }

    @Test
    void handleAudioFrequency_preservesSelectedBassStringMetadata() {
        controller.handleAudioFrequency(Map.of(
            "detectedHz", 41.2,
            "targetHz", 41.2,
            "stringLabel", "E",
            "note", "E1",
            "stringNumber", 1
        ));

        ArgumentCaptor<TuningUpdate> captor = ArgumentCaptor.forClass(TuningUpdate.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/tuning"), captor.capture());

        TuningUpdate update = captor.getValue();
        assertEquals("E", update.getString());
        assertEquals("E1", update.getNote());
        assertEquals(41.2, update.getTargetHz());
        assertEquals("in_tune", update.getStatus());
    }

    @Test
    void handleAudioFrequency_parsesSelectedStringMetadataAsStrings() {
        controller.handleAudioFrequency(Map.of(
            "detectedHz", "73.42",
            "targetHz", "73.42",
            "stringLabel", "D",
            "note", "D2",
            "stringNumber", "3"
        ));

        ArgumentCaptor<TuningUpdate> captor = ArgumentCaptor.forClass(TuningUpdate.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/tuning"), captor.capture());

        TuningUpdate update = captor.getValue();
        assertEquals("D", update.getString());
        assertEquals("D2", update.getNote());
        assertEquals(73.42, update.getTargetHz());
    }

    @Test
    void handleAudioFrequency_matchesMiddleString() {
        // 196 Hz is G3
        controller.handleAudioFrequency(Map.of("detectedHz", 196.0));

        ArgumentCaptor<TuningUpdate> captor = ArgumentCaptor.forClass(TuningUpdate.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/tuning"), captor.capture());

        assertEquals("G", captor.getValue().getString());
    }
}
