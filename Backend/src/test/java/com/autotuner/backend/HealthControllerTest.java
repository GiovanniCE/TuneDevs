// Automated test class for HealthControllerTest behavior; it provides assertions that the related backend behavior remains correct.

package com.autotuner.backend;

import com.autotuner.backend.controller.HealthController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HealthControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new HealthController()).build();
    }

    @Test
    void health_returns200() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }

    @Test
    void health_returnsStatusOk() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void health_returnsJson() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }
}
