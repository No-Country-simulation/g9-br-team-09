package br.com.g9.energiai.backend.config;

import br.com.g9.energiai.backend.client.ml.MlPredictionClient;
import br.com.g9.energiai.backend.client.ml.exception.MlPredictionClientException;
import br.com.g9.energiai.backend.repository.EnergyAnalysisRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "RUN_ORACLE_IT", matches = "true")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("oci")
class OracleAutonomousDatabaseIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EnergyAnalysisRepository energyAnalysisRepository;

    @MockitoBean
    private MlPredictionClient mlPredictionClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Long createdId;

    @AfterEach
    void deleteCreatedAnalysis() {
        if (createdId != null) {
            energyAnalysisRepository.deleteById(createdId);
        }
    }

    @Test
    @DisplayName("Deve validar Flyway e persistir uma análise no Oracle Autonomous Database")
    void shouldPersistAndReadAnalysisUsingOracleAutonomousDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertEquals("Oracle", connection.getMetaData().getDatabaseProductName());
            String currentSchema = connection.getSchema();

            if (currentSchema == null || currentSchema.isBlank()) {
                currentSchema = connection.getMetaData().getUserName();
            }

            assertTrue(
                currentSchema != null && !currentSchema.isBlank(),
                "Não foi possível identificar o schema atual da conexão Oracle"
            );

            try (ResultSet tables = connection.getMetaData().getTables(
                null,
                currentSchema,
                "ENERGY_ANALYSIS",
                new String[]{"TABLE"}
            )) {
                assertTrue(
                    tables.next(),
                    "ENERGY_ANALYSIS deve existir no schema atual após a migration do Flyway"
                );
            }
        }
        assertTrue(
            Arrays.stream(flyway.info().applied())
                .anyMatch(migration -> "1".equals(migration.getVersion().getVersion())),
            "Flyway deve aplicar ou validar a migration V1"
        );

        when(mlPredictionClient.predict(any())).thenThrow(new MlPredictionClientException("API indisponível"));
        String requestBody = """
            {
              "consumo_kwh": 420,
              "uso_horario_pico": true,
              "quantidade_equipamentos": 10,
              "tipo_imovel": "CASA",
              "horas_alto_consumo": 8
            }
            """;

        String responseBody = mockMvc.perform(post("/api/v1/analise-energetica")
                        .contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(responseBody);
        createdId = response.get("id").asLong();

        mockMvc.perform(get("/api/v1/analise-energetica/{id}", createdId).contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdId))
                .andExpect(jsonPath("$.consumo_kwh").value(420.0))
                .andExpect(jsonPath("$.uso_horario_pico").value(true))
                .andExpect(jsonPath("$.quantidade_equipamentos").value(10))
                .andExpect(jsonPath("$.tipo_imovel").value("CASA"))
                .andExpect(jsonPath("$.horas_alto_consumo").value(8));
    }
}
