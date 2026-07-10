package br.com.g9.energiai.backend.controller;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.enums.PropertyType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class EnergyAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    @DisplayName("Deve realizar análise energética com sucesso e retornar 200 OK")
    void shouldPerformAnalysisSuccessfully() throws Exception {
        // Cenário: Consumo ineficiente
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(
                500.0,
                true,
                10,
                PropertyType.CASA,
                8
        );

        mockMvc.perform(post("/energy-analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.categoria").value("INEFICIENTE"))
                .andExpect(jsonPath("$.custoEstimadoMensal").value(375.00))
                .andExpect(jsonPath("$.recomendacoes").isArray())
                .andExpect(jsonPath("$.fonteClassificacao").value("RULE_BASED"));
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request quando os dados de entrada forem inválidos")
    void shouldReturnBadRequestWhenInputIsInvalid() throws Exception {
        // Cenário: Consumo negativo
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(
                -100.0,
                true,
                10,
                PropertyType.CASA,
                8
        );

        mockMvc.perform(post("/energy-analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
