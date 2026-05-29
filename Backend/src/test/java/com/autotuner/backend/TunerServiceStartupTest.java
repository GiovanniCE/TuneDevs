// Automated test class for TunerServiceStartupTest behavior; it provides assertions that the related backend behavior remains correct.

package com.autotuner.backend;

import com.autotuner.backend.model.TuningUpdate;
import com.autotuner.backend.service.TunerService;
import com.autotuner.backend.service.TuningUpdateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TunerServiceStartupTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private TuningUpdateMapper tuningUpdateMapper;

    private TunerService tunerService;

    @BeforeEach
    void setUp() {
        tunerService = new TunerService(messagingTemplate, tuningUpdateMapper);
    }

    // --- Mock mode startup ---

    @Test
    void startTuner_mockMode_createsAliveWorkerThread() throws Exception {
        setTunerMode("mock");

        tunerService.startTuner();

        Thread workerThread = getWorkerThread();
        assertThat(workerThread).isNotNull();
        assertThat(workerThread.isAlive()).isTrue();

        tunerService.stopTuner();
    }

    @Test
    void startTuner_mockMode_broadcastsUpdates() throws Exception {
        setTunerMode("mock");

        TuningUpdate mockUpdate = new TuningUpdate("tuning_update", 1L, "E", "E2", 82.41, 82.0, -1.0, "flat");
        when(tuningUpdateMapper.mockUpdate(anyString(), anyString(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(mockUpdate);

        tunerService.startTuner();
        Thread.sleep(1100); // Wait for at least one 800 ms loop iteration
        tunerService.stopTuner();
        Thread.sleep(200);  // Wait for the worker thread to fully exit

        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/tuning"), eq(mockUpdate));
    }

    @Test
    void stopTuner_mockMode_threadStopsRunning() throws Exception {
        setTunerMode("mock");

        tunerService.startTuner();
        Thread.sleep(100);
        tunerService.stopTuner();

        Thread workerThread = getWorkerThread();
        workerThread.join(2000); // Block until thread exits (up to 2 s)
        assertThat(workerThread.isAlive()).isFalse();
    }

    // --- Hardware mode startup ---

    @Test
    void startTuner_hardwareMode_doesNotThrow() throws Exception {
        setTunerMode("hardware");

        // Succeeds whether or not python3 is on PATH — exception is caught internally
        assertDoesNotThrow(() -> tunerService.startTuner());

        Thread.sleep(100);
        tunerService.stopTuner();
    }

    // --- readProcessOutput: invalid line recovery ---

    @Test
    void readProcessOutput_skipsInvalidJsonAndContinues() throws Exception {
        Field runningField = TunerService.class.getDeclaredField("running");
        runningField.setAccessible(true);
        ((AtomicBoolean) runningField.get(tunerService)).set(true);

        String invalidLine = "WARN: GPIO not available";
        String validLine = "{\"frequency\":82.4,\"note\":\"E2\",\"cents\":0,"
                + "\"target_frequency\":82.41,\"string_number\":1,\"status\":\"IN_TUNE\"}";

        Process mockProcess = mock(Process.class);
        when(mockProcess.getInputStream()).thenReturn(
                new ByteArrayInputStream((invalidLine + "\n" + validLine + "\n").getBytes()));

        Field processField = TunerService.class.getDeclaredField("pythonProcess");
        processField.setAccessible(true);
        processField.set(tunerService, mockProcess);

        TuningUpdate validUpdate = new TuningUpdate("tuning_update", 1L, "E", "E2", 82.41, 82.4, 0.0, "in_tune");
        when(tuningUpdateMapper.fromPythonJson(invalidLine)).thenThrow(new RuntimeException("not valid JSON"));
        when(tuningUpdateMapper.fromPythonJson(validLine)).thenReturn(validUpdate);

        Method method = TunerService.class.getDeclaredMethod("readProcessOutput");
        method.setAccessible(true);
        method.invoke(tunerService);

        // The valid line after the invalid one is still broadcast
        verify(messagingTemplate).convertAndSend("/topic/tuning", validUpdate);
        // No other messages were sent (the invalid line was skipped entirely)
        verifyNoMoreInteractions(messagingTemplate);
    }

    // --- Helpers ---

    private void setTunerMode(String mode) throws Exception {
        Field modeField = TunerService.class.getDeclaredField("tunerMode");
        modeField.setAccessible(true);
        modeField.set(tunerService, mode);
    }

    private Thread getWorkerThread() throws Exception {
        Field workerThreadField = TunerService.class.getDeclaredField("workerThread");
        workerThreadField.setAccessible(true);
        return (Thread) workerThreadField.get(tunerService);
    }
}
