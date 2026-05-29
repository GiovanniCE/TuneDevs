// Automated test class for TunerServiceTest behavior; it provides assertions that the related backend behavior remains correct.

package com.autotuner.backend.service;

import com.autotuner.backend.model.TuningUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TunerService.
 * Instantiated directly (no Spring context) to avoid @PostConstruct launching threads.
 */
@ExtendWith(MockitoExtension.class)
class TunerServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private TuningUpdateMapper tuningUpdateMapper;

    @Mock
    private Process pythonProcess;

    private TunerService tunerService;

    @BeforeEach
    void setUp() throws Exception {
        tunerService = new TunerService(messagingTemplate, tuningUpdateMapper);
        // Set mode to mock to avoid hardware init
        setField("tunerMode", "mock");
        setField("tunerInput", "gpio");
    }

    @Test
    void stopTuner_setsRunningToFalse() throws Exception {
        // Start it first
        getRunning().set(true);

        tunerService.stopTuner();

        assertFalse(getRunning().get());
    }

    @Test
    void stopTuner_destroysPythonProcess_whenPresent() throws Exception {
        setField("pythonProcess", pythonProcess);
        getRunning().set(true);

        tunerService.stopTuner();

        verify(pythonProcess).destroy();
    }

    @Test
    void stopTuner_handlesNullProcess() {
        // pythonProcess is null by default – should not throw
        assertDoesNotThrow(() -> tunerService.stopTuner());
    }

    @Test
    void readProcessOutput_broadcastsTuningUpdates() throws Exception {
        setField("pythonProcess", pythonProcess);
        getRunning().set(true);

        String line = "{\"frequency\":82.4,\"note\":\"E2\",\"cents\":-3.0,\"target_frequency\":82.41,\"string_number\":1,\"status\":\"FLAT\"}";
        when(pythonProcess.getInputStream())
                .thenReturn(new ByteArrayInputStream((line + "\n").getBytes()));

        TuningUpdate mockUpdate = new TuningUpdate("tuning_update", 123L, "E", "E2", 82.41, 82.4, -3.0, "flat");
        when(tuningUpdateMapper.fromPythonJson(line)).thenReturn(mockUpdate);

        invokeReadProcessOutput();

        verify(messagingTemplate).convertAndSend(eq("/topic/tuning"), eq(mockUpdate));
    }

    @Test
    void readProcessOutput_skipsInvalidLines() throws Exception {
        setField("pythonProcess", pythonProcess);
        getRunning().set(true);

        String invalidLine = "not json at all";
        when(pythonProcess.getInputStream())
                .thenReturn(new ByteArrayInputStream((invalidLine + "\n").getBytes()));
        when(tuningUpdateMapper.fromPythonJson(invalidLine)).thenThrow(new RuntimeException("parse error"));

        // Should not throw, just skip
        assertDoesNotThrow(() -> invokeReadProcessOutput());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(TuningUpdate.class));
    }

    @Test
    void readProcessOutput_stopsWhenRunningIsFalse() throws Exception {
        setField("pythonProcess", pythonProcess);
        getRunning().set(false);

        String line = "{\"frequency\":82.4}";
        when(pythonProcess.getInputStream())
                .thenReturn(new ByteArrayInputStream((line + "\n").getBytes()));

        invokeReadProcessOutput();

        verifyNoInteractions(tuningUpdateMapper);
    }

    @Test
    void startTuner_browserModeDoesNotLaunchWorkerOrPythonProcess() throws Exception {
        setField("tunerMode", "browser");

        tunerService.startTuner();

        assertTrue(getRunning().get());
        assertNull(getField("workerThread"));
        assertNull(getField("pythonProcess"));
        verifyNoInteractions(messagingTemplate);
        verifyNoInteractions(tuningUpdateMapper);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void setField(String name, Object value) throws Exception {
        Field field = TunerService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(tunerService, value);
    }

    private java.util.concurrent.atomic.AtomicBoolean getRunning() throws Exception {
        Field field = TunerService.class.getDeclaredField("running");
        field.setAccessible(true);
        return (java.util.concurrent.atomic.AtomicBoolean) field.get(tunerService);
    }

    private Object getField(String name) throws Exception {
        Field field = TunerService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(tunerService);
    }

    private void invokeReadProcessOutput() throws Exception {
        Method method = TunerService.class.getDeclaredMethod("readProcessOutput");
        method.setAccessible(true);
        method.invoke(tunerService);
    }
}
