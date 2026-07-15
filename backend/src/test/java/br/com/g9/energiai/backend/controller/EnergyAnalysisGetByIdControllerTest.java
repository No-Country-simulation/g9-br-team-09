package br.com.g9.energiai.backend.controller;

import br.com.g9.energiai.backend.entity.EnergyAnalysisEntity;
import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import br.com.g9.energiai.backend.repository.EnergyAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class EnergyAnalysisGetByIdControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EnergyAnalysisRepository energyAnalysisRepository;

    @BeforeEach
    void setup() {
        energyAnalysisRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve retornar detalhes completos da análise quando o ID existir")
    void shouldReturnDetailedAnalysisWhenIdExists() throws Exception {
        EnergyAnalysisEntity analysis = EnergyAnalysisEntity.builder()
                .consumoKwh(420.0)
                .usoHorarioPico(true)
                .quantidadeEquipamentos(10)
                .tipoImovel(PropertyType.CASA)
                .horasAltoConsumo(8)
                .categoria(EnergyCategory.INEFICIENTE)
                .probabilidade(0.95)
                .score(95)
                .custoEstimadoMensal(new BigDecimal("315.00"))
                .fonteClassificacao(ClassificationSource.RULE_BASED)
                .recomendacoes(List.of("Dica 1"))
                .build();

        EnergyAnalysisEntity saved = energyAnalysisRepository.save(analysis);

        mockMvc.perform(get("/api/v1/analise-energetica/{id}", saved.getId())
                        .contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.consumo_kwh").value(420.0))
                .andExpect(jsonPath("$.uso_horario_pico").value(true))
                .andExpect(jsonPath("$.quantidade_equipamentos").value(10))
                .andExpect(jsonPath("$.tipo_imovel").value("CASA"))
                .andExpect(jsonPath("$.horas_alto_consumo").value(8))
                .andExpect(jsonPath("$.categoria").value("INEFICIENTE"))
                .andExpect(jsonPath("$.probabilidade").value(0.95))
                .andExpect(jsonPath("$.score").value(95))
                .andExpect(jsonPath("$.custo_estimado_mensal").value(315.00))
                .andExpect(jsonPath("$.recomendacoes[0]").value("Dica 1"))
                .andExpect(jsonPath("$.fonte_classificacao").value("RULE_BASED"))
                .andExpect(jsonPath("$.criado_em").exists());
    }

    @Test
    @DisplayName("Deve retornar 404 para ID inexistente")
    void shouldReturn404ForMissingId() throws Exception {
        mockMvc.perform(get("/api/v1/analise-energetica/{id}", 999L)
                        .contextPath("/api/v1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("Análise não encontrada com o ID: 999"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
