package br.com.g9.energiai.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthCheckIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HealthEndpointGroups healthEndpointGroups;

    @Autowired
    private HealthContributorRegistry healthContributorRegistry;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Deve expor health geral sem detalhes ou componentes internos")
    void shouldExposeHealthCheckWithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/api/v1/actuator/health").contextPath("/api/v1"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/*+json")))
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.details").doesNotExist())
            .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    @DisplayName("Deve expor liveness UP sem detalhes e sem dependencia do banco")
    void shouldExposeLivenessWithoutDatabaseDependency() throws Exception {
        mockMvc.perform(get("/api/v1/actuator/health/liveness").contextPath("/api/v1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.details").doesNotExist())
            .andExpect(jsonPath("$.components").doesNotExist());

        assertThat(healthEndpointGroups.get("liveness").isMember("livenessState")).isTrue();
        assertThat(healthEndpointGroups.get("liveness").isMember("db")).isFalse();
    }

    @Test
    @DisplayName("Deve expor readiness UP com banco obrigatorio e sem FastAPI")
    void shouldExposeReadinessWithDatabaseAndWithoutFastApiDependency() throws Exception {
        mockMvc.perform(get("/api/v1/actuator/health/readiness").contextPath("/api/v1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.details").doesNotExist())
            .andExpect(jsonPath("$.components").doesNotExist());

        assertThat(healthEndpointGroups.get("readiness").isMember("readinessState")).isTrue();
        assertThat(healthEndpointGroups.get("readiness").isMember("db")).isTrue();
        assertThat(healthEndpointGroups.get("readiness").isMember("livenessState")).isFalse();
        assertThat(healthEndpointGroups.get("readiness").isMember("mlApi")).isFalse();
        assertThat(healthContributorRegistry.getContributor("mlApi")).isNull();
    }

    @Test
    @DisplayName("Deve retornar readiness indisponivel enquanto recusa trafego")
    void shouldExposeOutOfServiceReadinessWhenTrafficIsRefused() throws Exception {
        AvailabilityChangeEvent.publish(applicationContext, ReadinessState.REFUSING_TRAFFIC);

        try {
            mockMvc.perform(get("/api/v1/actuator/health/readiness").contextPath("/api/v1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"))
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.components").doesNotExist());
        }
        finally {
            AvailabilityChangeEvent.publish(applicationContext, ReadinessState.ACCEPTING_TRAFFIC);
        }
    }

    @Test
    @DisplayName("Não deve expor endpoints sensíveis do Actuator")
    void shouldNotExposeSensitiveActuatorEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/actuator/env").contextPath("/api/v1"))
            .andExpect(status().isNotFound());
    }
}
