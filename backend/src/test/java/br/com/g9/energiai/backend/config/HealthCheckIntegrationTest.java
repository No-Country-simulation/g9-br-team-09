package br.com.g9.energiai.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class HealthCheckIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Deve expor apenas o health check operacional sem detalhes internos")
    void shouldExposeHealthCheckWithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/api/v1/actuator/health").contextPath("/api/v1"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/*+json")))
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.details").doesNotExist())
            .andExpect(jsonPath("$.components").doesNotExist())
            .andExpect(jsonPath("$.groups").doesNotExist());
    }

    @Test
    @DisplayName("Não deve expor endpoints sensíveis do Actuator")
    void shouldNotExposeSensitiveActuatorEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/actuator/env").contextPath("/api/v1"))
            .andExpect(status().isNotFound());
    }
}
