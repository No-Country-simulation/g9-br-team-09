package br.com.g9.energiai.backend.config;

import br.com.g9.energiai.backend.client.ml.MlPredictionClient;
import br.com.g9.energiai.backend.client.ml.exception.MlPredictionClientException;
import br.com.g9.energiai.backend.entity.AppUser;
import br.com.g9.energiai.backend.enums.UserRole;
import br.com.g9.energiai.backend.repository.EnergyAnalysisRepository;
import br.com.g9.energiai.backend.repository.RefreshTokenRepository;
import br.com.g9.energiai.backend.repository.UserRepository;
import br.com.g9.energiai.backend.service.JwtTokenService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private MlPredictionClient mlPredictionClient;

    @Autowired
    private ObjectMapper objectMapper;
    private Long createdId;
    private Long createdUserId;

    @AfterEach
    void deleteCreatedAnalysis() {
        if (createdId != null) {
            energyAnalysisRepository.deleteById(createdId);
        }
        if (createdUserId != null) {
            refreshTokenRepository.deleteAllByUserId(createdUserId);
            userRepository.deleteById(createdUserId);
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

            try (ResultSet tables = connection.getMetaData().getTables(
                    null,
                    currentSchema,
                    "REFRESH_TOKEN",
                    new String[]{"TABLE"}
            )) {
                assertTrue(tables.next(), "REFRESH_TOKEN deve existir após a migration V4");
            }
        }
        assertTrue(
            Arrays.stream(flyway.info().applied())
                .anyMatch(migration -> "4".equals(migration.getVersion().getVersion())),
            "Flyway deve aplicar ou validar a migration V4"
        );

        AppUser user = userRepository.saveAndFlush(AppUser.builder()
                .name("Oracle Integration Test")
                .email("oracle-it-" + System.nanoTime() + "@example.com")
                .passwordHash("unused-password-hash")
                .role(UserRole.USER)
                .active(true)
                .build());
        createdUserId = user.getId();
        String accessToken = jwtTokenService.generateToken(user);

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
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(responseBody);
        createdId = response.get("id").asLong();

        mockMvc.perform(get("/api/v1/analise-energetica/{id}", createdId)
                        .contextPath("/api/v1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdId))
                .andExpect(jsonPath("$.consumo_kwh").value(420.0))
                .andExpect(jsonPath("$.uso_horario_pico").value(true))
                .andExpect(jsonPath("$.quantidade_equipamentos").value(10))
                .andExpect(jsonPath("$.tipo_imovel").value("CASA"))
                .andExpect(jsonPath("$.horas_alto_consumo").value(8));
    }
}
