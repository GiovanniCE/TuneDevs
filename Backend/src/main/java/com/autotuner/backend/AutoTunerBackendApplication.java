// Spring Boot application entry point for the Auto Tuner backend; it provides running backend application context and embedded web server.

package com.autotuner.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AutoTunerBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoTunerBackendApplication.class, args);
    }
}
