// Automated test class for TunerServiceTest behavior; it provides assertions that the related backend behavior remains correct.

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
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

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

        Field processField = TunerService.class.getDeclaredField("pythonProcess");
        processField.setAccessible(true);
        processField.set(tunerService, pythonProcess);

        Field runningField = TunerService.class.getDeclaredField("running");
        runningField.setAccessible(true);
        AtomicBoolean running = (AtomicBoolean) runningField.get(tunerService);
        running.set(true);
    }

    @Test
    void stopTuner_destroysProcess() {
        tunerService.stopTuner();

        verify(pythonProcess).destroy();
    }

    @Test
    void stopTuner_setsIsRunningFalse() throws Exception {
        tunerService.stopTuner();

        Field runningField = TunerService.class.getDeclaredField("running");
        runningField.setAccessible(true);
        AtomicBoolean running = (AtomicBoolean) runningField.get(tunerService);

        assertFalse(running.get());
    }

    @Test
    void readProcessOutput_broadcastsEachLineToBroker() throws Exception {
        String line1 = "{\"frequency\":82.4,\"note\":\"E2\"}";
        String line2 = "{\"frequency\":110.0,\"note\":\"A2\"}";
        InputStream fakeOutput = new ByteArrayInputStream((line1 + "\n" + line2 + "\n").getBytes());
        when(pythonProcess.getInputStream()).thenReturn(fakeOutput);

        TuningUpdate update1 = new TuningUpdate("tuning_update", 1L, "E", "E2", 82.41, 82.40, -1.0, "flat");
        TuningUpdate update2 = new TuningUpdate("tuning_update", 1L, "A", "A2", 110.00, 110.00, 0.0, "in_tune");
        when(tuningUpdateMapper.fromPythonJson(line1)).thenReturn(update1);
        when(tuningUpdateMapper.fromPythonJson(line2)).thenReturn(update2);

        invokeReadProcessOutput();

        verify(messagingTemplate).convertAndSend("/topic/tuning", update1);
        verify(messagingTemplate).convertAndSend("/topic/tuning", update2);
    }

    @Test
    void readProcessOutput_sendsToCorrectTopic() throws Exception {
        String message = "{\"frequency\":82.4,\"note\":\"E2\"}";
        InputStream fakeOutput = new ByteArrayInputStream((message + "\n").getBytes());
        when(pythonProcess.getInputStream()).thenReturn(fakeOutput);

        TuningUpdate update = new TuningUpdate("tuning_update", 1L, "E", "E2", 82.41, 82.40, -1.0, "flat");
        when(tuningUpdateMapper.fromPythonJson(message)).thenReturn(update);

        invokeReadProcessOutput();

        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/tuning"), eq(update));
    }

    @Test
    void readProcessOutput_stopsWhenIsRunningFalse() throws Exception {
        Field runningField = TunerService.class.getDeclaredField("running");
        runningField.setAccessible(true);
        AtomicBoolean running = (AtomicBoolean) runningField.get(tunerService);
        running.set(false);

        String message = "{\"frequency\":82.4,\"note\":\"E2\"}";
        InputStream fakeOutput = new ByteArrayInputStream((message + "\n").getBytes());
        when(pythonProcess.getInputStream()).thenReturn(fakeOutput);

        invokeReadProcessOutput();

        verifyNoInteractions(messagingTemplate);
    }

    private void invokeReadProcessOutput() throws Exception {
        Method method = TunerService.class.getDeclaredMethod("readProcessOutput");
        method.setAccessible(true);
        method.invoke(tunerService);
    }
}
