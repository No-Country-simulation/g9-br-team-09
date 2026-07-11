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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    @DisplayName("Deve realizar análise energética com sucesso pela URL pública e retornar resposta completa incluindo ID")
    void shouldPerformAnalysisSuccessfully() throws Exception {
        String requestBody = """
            {
              "consumo_kwh": 500,
              "uso_horario_pico": true,
              "quantidade_equipamentos": 10,
              "tipo_imovel": "CASA",
              "horas_alto_consumo": 8
            }
            """;

        mockMvc.perform(post("/api/v1/analise-energetica")
                        .contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.categoria").value("INEFICIENTE"))
                .andExpect(jsonPath("$.probabilidade").value(0.95))
                .andExpect(jsonPath("$.score").value(95))
                .andExpect(jsonPath("$.custo_estimado_mensal").value(375.00))
                .andExpect(jsonPath("$.fonte_classificacao").value("RULE_BASED"))
                .andExpect(jsonPath("$.custoEstimadoMensal").doesNotExist())
                .andExpect(jsonPath("$.fonteClassificacao").doesNotExist())
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
              "consumo_kwh": -100,
              "uso_horario_pico": true,
              "quantidade_equipamentos": 10,
              "tipo_imovel": "CASA",
              "horas_alto_consumo": 8
            }
            """;

        mockMvc.perform(post("/api/v1/analise-energetica")
                        .contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message", containsString("consumo_kwh")))
                .andExpect(jsonPath("$.message", not(containsString("consumoKwh"))))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Não deve expor a rota antiga como contrato público")
    void shouldNotServeLegacyRoute() throws Exception {
        String requestBody = """
            {
              "consumo_kwh": 500,
              "uso_horario_pico": true,
              "quantidade_equipamentos": 10,
              "tipo_imovel": "CASA",
              "horas_alto_consumo": 8
            }
            """;

        mockMvc.perform(post("/api/v1/analises-energeticas")
                        .contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Deve expor o contrato final no OpenAPI")
    void shouldExposeFinalContractInOpenApi() throws Exception {
        mockMvc.perform(get("/api/v1/v3/api-docs").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("\"url\":\"http://localhost/api/v1\"")))
                .andExpect(content().string(containsString("\"/analise-energetica\"")))
                .andExpect(content().string(not(containsString("\"/analises-energeticas\""))))
                .andExpect(content().string(containsString("\"consumo_kwh\"")))
                .andExpect(content().string(containsString("\"uso_horario_pico\"")))
                .andExpect(content().string(containsString("\"quantidade_equipamentos\"")))
                .andExpect(content().string(containsString("\"tipo_imovel\"")))
                .andExpect(content().string(containsString("\"horas_alto_consumo\"")))
                .andExpect(content().string(containsString("\"custo_estimado_mensal\"")))
                .andExpect(content().string(containsString("\"fonte_classificacao\"")));
    }
}
