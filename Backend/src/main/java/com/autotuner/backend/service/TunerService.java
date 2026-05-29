// Starts mock, browser, or hardware tuner data flow and publishes tuning updates; it provides tuningUpdate messages sent to WebSocket subscribers.

package com.autotuner.backend.service;

import com.autotuner.backend.model.TuningUpdate;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Starts either:
 * 1) browser mode: no backend tuner process; browser audio drives tuning updates
 * 2) hardware mode: runs the Python tuner process and forwards normalized updates
 * 3) mock mode: emits simulated tuning updates for laptop/local development
 */
@Service
public class TunerService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SimpMessagingTemplate messagingTemplate;
    private final TuningUpdateMapper tuningUpdateMapper;

    @Value("${tuner.mode:browser}")
    private String tunerMode;

    @Value("${tuner.input:gpio}")
    private String tunerInput;

    private Process pythonProcess;
    private Thread workerThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private static final List<StringSpec> STANDARD_GUITAR_STRINGS = List.of(
            new StringSpec("E", "E2", 82.41),
            new StringSpec("A", "A2", 110.00),
            new StringSpec("D", "D3", 146.83),
            new StringSpec("G", "G3", 196.00),
            new StringSpec("B", "B3", 246.94),
            new StringSpec("e", "E4", 329.63)
    );

    private static final List<StringSpec> STANDARD_BASS_STRINGS = List.of(
            new StringSpec("E", "E1", 41.20),
            new StringSpec("A", "A1", 55.00),
            new StringSpec("D", "D2", 73.42),
            new StringSpec("G", "G2", 98.00)
    );

    @Value("${tuner.instrument:guitar}")
    private String tunerInstrument;

    @Autowired
    public TunerService(
            SimpMessagingTemplate messagingTemplate,
            TuningUpdateMapper tuningUpdateMapper
    ) {
        this.messagingTemplate = messagingTemplate;
        this.tuningUpdateMapper = tuningUpdateMapper;
    }

    @PostConstruct
    public void startTuner() {
        running.set(true);

        if ("mock".equalsIgnoreCase(tunerMode)) {
            startMockMode();
        } else if ("hardware".equalsIgnoreCase(tunerMode)) {
            startHardwareMode();
        } else {
            System.out.println("Tuner started in BROWSER mode. Waiting for frontend audio updates.");
        }
    }

    private void startMockMode() {
        // Mock mode keeps the frontend/demo usable without GPIO hardware.
        workerThread = new Thread(this::runMockLoop, "tuner-mock-thread");
        workerThread.start();
        System.out.println("Tuner started in MOCK mode.");
    }

    private void runMockLoop() {
        try {
            Random random = new Random();
            // Match the mock data to the configured instrument so the tuner UI
            // receives realistic string labels and frequency ranges.
            List<StringSpec> strings = "bass".equalsIgnoreCase(tunerInstrument)
                    ? STANDARD_BASS_STRINGS
                    : STANDARD_GUITAR_STRINGS;

            while (running.get()) {
                StringSpec spec = strings.get(random.nextInt(strings.size()));

                // Convert a random cents offset back to Hz so mock output has
                // the same shape as real detected audio.
                double centsOff = random.nextDouble() * 60.0 - 30.0; // [-30, +30]
                double detectedHz = spec.targetHz() * Math.pow(2.0, centsOff / 1200.0);

                TuningUpdate update = tuningUpdateMapper.mockUpdate(
                        spec.label(),
                        spec.note(),
                        spec.targetHz(),
                        round(detectedHz),
                        round(centsOff)
                );

                messagingTemplate.convertAndSend("/topic/tuning", update);

                Thread.sleep(800);
            }
        } catch (Exception e) {
            if (running.get()) {
                e.printStackTrace();
            }
        }
    }

    private void startHardwareMode() {
        try {
            // Copy scripts out of the packaged resources so Python can import
            // sibling modules from a normal filesystem directory.
            Path tempDir = Files.createTempDirectory("tuner_scripts");
            File tunerScript = copyResourceToTemp(tempDir, "tuner.py");
            copyResourceToTemp(tempDir, "freqCounter.py");
            copyResourceToTemp(tempDir, "motor_control.py");
            copyResourceToTemp(tempDir, "audio_interface.py");

            ProcessBuilder pb = new ProcessBuilder("python3", "-u", tunerScript.getAbsolutePath());
            pb.directory(tempDir.toFile());
            // Let the same Python entry point choose GPIO, audio interface, or
            // other future input modes from configuration.
            pb.environment().put("TUNER_INPUT", tunerInput);
            pb.redirectErrorStream(true);

            pythonProcess = pb.start();
            System.out.println("Tuner Python process started: " + tunerScript.getAbsolutePath());

            workerThread = new Thread(this::readProcessOutput, "tuner-hardware-thread");
            workerThread.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void readProcessOutput() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(pythonProcess.getInputStream()))) {
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                try {
                    // The Python process writes one JSON event per line.
                    TuningUpdate update = tuningUpdateMapper.fromPythonJson(line);
                    messagingTemplate.convertAndSend("/topic/tuning", update);
                } catch (Exception parseException) {
                    System.err.println("Skipping invalid tuner output line: " + line);
                }
            }
        } catch (Exception e) {
            if (running.get()) {
                e.printStackTrace();
            }
        }
    }

    private File copyResourceToTemp(Path tempDir, String resourceName) throws Exception {
        // ClassPathResource works both from the IDE and from a packaged jar.
        ClassPathResource resource = new ClassPathResource(resourceName);
        Path destination = tempDir.resolve(resourceName);
        try (InputStream in = resource.getInputStream()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        return destination.toFile();
    }

    @PreDestroy
    public void stopTuner() {
        // Stop the loop first so shutdown errors are not reported as live
        // runtime failures.
        running.set(false);

        if (pythonProcess != null) {
            pythonProcess.destroy();
            System.out.println("Tuner Python process stopped.");
        }

        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record StringSpec(String label, String note, double targetHz) {}
}
