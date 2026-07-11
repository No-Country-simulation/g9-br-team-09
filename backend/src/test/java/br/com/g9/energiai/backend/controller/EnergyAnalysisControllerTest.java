package br.com.g9.energiai.backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class EnergyAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Deve realizar análise energética com sucesso pela URL pública e retornar resposta completa")
    void shouldPerformAnalysisSuccessfully() throws Exception {
        String requestBody = """
            {
              "consumoKwh": 500,
              "usoHorarioPico": true,
              "quantidadeEquipamentos": 10,
              "tipoImovel": "CASA",
              "horasAltoConsumo": 8
            }
            """;

        mockMvc.perform(post("/api/v1/analises-energeticas")
                .contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.categoria").value("INEFICIENTE"))
            .andExpect(jsonPath("$.probabilidade").value(0.95))
            .andExpect(jsonPath("$.score").value(95))
            .andExpect(jsonPath("$.custoEstimadoMensal").value(375.00))
            .andExpect(jsonPath("$.fonteClassificacao").value("RULE_BASED"))
            .andExpect(jsonPath("$.recomendacoes").isArray())
            .andExpect(jsonPath("$.recomendacoes.length()").value(4))
            .andExpect(jsonPath("$.recomendacoes", containsInAnyOrder(
                "Reduzir o uso de equipamentos durante horários de pico.",
                "Avaliar equipamentos com alto consumo energético.",
                "Distribuir o consumo ao longo do dia.",
                "Verificar a eficiência energética dos equipamentos."
            )));
    }

    @Test
    @DisplayName("Deve retornar 400 quando os dados de entrada forem inválidos")
    void shouldReturnBadRequestWhenInputIsInvalid() throws Exception {
        String requestBody = """
            {
              "consumoKwh": -100,
              "usoHorarioPico": true,
              "quantidadeEquipamentos": 10,
              "tipoImovel": "CASA",
              "horasAltoConsumo": 8
            }
            """;

        mockMvc.perform(post("/api/v1/analises-energeticas")
                .contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").isNotEmpty())
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
