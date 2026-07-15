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
class EnergyAnalysisDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EnergyAnalysisRepository energyAnalysisRepository;

    @BeforeEach
    void setup() {
        energyAnalysisRepository.deleteAll();

        EnergyAnalysisEntity e1 = EnergyAnalysisEntity.builder()
                .consumoKwh(100.0).usoHorarioPico(false).quantidadeEquipamentos(2)
                .tipoImovel(PropertyType.APARTAMENTO).horasAltoConsumo(1)
                .categoria(EnergyCategory.EFICIENTE).probabilidade(0.1).score(10)
                .custoEstimadoMensal(new BigDecimal("75.00"))
                .fonteClassificacao(ClassificationSource.RULE_BASED).recomendacoes(List.of())
                .build();

        EnergyAnalysisEntity i1 = EnergyAnalysisEntity.builder()
                .consumoKwh(500.0).usoHorarioPico(true).quantidadeEquipamentos(10)
                .tipoImovel(PropertyType.CASA).horasAltoConsumo(8)
                .categoria(EnergyCategory.INEFICIENTE).probabilidade(0.9).score(90)
                .custoEstimadoMensal(new BigDecimal("375.00"))
                .fonteClassificacao(ClassificationSource.RULE_BASED).recomendacoes(List.of())
                .build();

        EnergyAnalysisEntity i2 = EnergyAnalysisEntity.builder()
                .consumoKwh(600.0).usoHorarioPico(true).quantidadeEquipamentos(12)
                .tipoImovel(PropertyType.CASA).horasAltoConsumo(10)
                .categoria(EnergyCategory.INEFICIENTE).probabilidade(0.95).score(95)
                .custoEstimadoMensal(new BigDecimal("450.00"))
                .fonteClassificacao(ClassificationSource.RULE_BASED).recomendacoes(List.of())
                .build();

        energyAnalysisRepository.saveAll(List.of(e1, i1, i2));
    }

    @Test
    @DisplayName("Deve retornar resumo estatístico correto para o dashboard")
    void shouldReturnCorrectDashboardSummary() throws Exception {
        mockMvc.perform(get("/api/v1/analise-energetica/resumo").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.total_analises").value(3))
                .andExpect(jsonPath("$.media_consumo_kwh").value(400.0))
                .andExpect(jsonPath("$.media_custo_mensal").value(300.00))
                .andExpect(jsonPath("$.total_eficiente").value(1))
                .andExpect(jsonPath("$.total_moderado").value(0))
                .andExpect(jsonPath("$.total_ineficiente").value(2));
    }

    @Test
    @DisplayName("Deve retornar valores zerados quando não houver análises")
    void shouldReturnZerosWhenNoDataExists() throws Exception {
        energyAnalysisRepository.deleteAll();

        mockMvc.perform(get("/api/v1/analise-energetica/resumo").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_analises").value(0))
                .andExpect(jsonPath("$.media_consumo_kwh").value(0.0))
                .andExpect(jsonPath("$.media_custo_mensal").value(0.0))
                .andExpect(jsonPath("$.total_eficiente").value(0))
                .andExpect(jsonPath("$.total_moderado").value(0))
                .andExpect(jsonPath("$.total_ineficiente").value(0));
    }
}
