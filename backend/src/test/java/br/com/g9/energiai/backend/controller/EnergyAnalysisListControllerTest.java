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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class EnergyAnalysisListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EnergyAnalysisRepository energyAnalysisRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        energyAnalysisRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve retornar histórico vazio paginado com 200 OK")
    void shouldReturnEmptyPaginatedHistory() throws Exception {
        mockMvc.perform(get("/api/v1/analise-energetica").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.analises").isArray())
                .andExpect(jsonPath("$.analises").isEmpty())
                .andExpect(jsonPath("$.pagina_atual").value(0))
                .andExpect(jsonPath("$.tamanho_pagina").value(20))
                .andExpect(jsonPath("$.total_elementos").value(0))
                .andExpect(jsonPath("$.total_paginas").value(0));
    }

    @Test
    @DisplayName("Deve ordenar o histórico da análise mais recente para a mais antiga")
    void shouldOrderHistoryByCreatedAtDescending() throws Exception {
        persistAnalysis(EnergyCategory.EFICIENTE, 25, LocalDateTime.of(2026, 7, 12, 18, 30));
        persistAnalysis(EnergyCategory.INEFICIENTE, 95, LocalDateTime.of(2026, 7, 13, 18, 30));

        mockMvc.perform(get("/api/v1/analise-energetica").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analises[0].categoria").value("INEFICIENTE"))
                .andExpect(jsonPath("$.analises[0].score").value(95))
                .andExpect(jsonPath("$.analises[1].categoria").value("EFICIENTE"))
                .andExpect(jsonPath("$.analises[1].score").value(25));
    }

    @Test
    @DisplayName("Deve retornar metadados corretos ao paginar o histórico")
    void shouldPaginateHistory() throws Exception {
        persistAnalysis(EnergyCategory.EFICIENTE, 10, LocalDateTime.of(2026, 7, 9, 18, 30));
        persistAnalysis(EnergyCategory.MODERADO, 20, LocalDateTime.of(2026, 7, 10, 18, 30));
        persistAnalysis(EnergyCategory.MODERADO, 30, LocalDateTime.of(2026, 7, 11, 18, 30));
        persistAnalysis(EnergyCategory.INEFICIENTE, 40, LocalDateTime.of(2026, 7, 12, 18, 30));
        persistAnalysis(EnergyCategory.INEFICIENTE, 50, LocalDateTime.of(2026, 7, 13, 18, 30));

        mockMvc.perform(get("/api/v1/analise-energetica")
                        .contextPath("/api/v1")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analises.length()").value(2))
                .andExpect(jsonPath("$.analises[0].score").value(50))
                .andExpect(jsonPath("$.analises[1].score").value(40))
                .andExpect(jsonPath("$.pagina_atual").value(0))
                .andExpect(jsonPath("$.tamanho_pagina").value(2))
                .andExpect(jsonPath("$.total_elementos").value(5))
                .andExpect(jsonPath("$.total_paginas").value(3));

        mockMvc.perform(get("/api/v1/analise-energetica")
                        .contextPath("/api/v1")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analises.length()").value(2))
                .andExpect(jsonPath("$.analises[0].score").value(30))
                .andExpect(jsonPath("$.analises[1].score").value(20))
                .andExpect(jsonPath("$.pagina_atual").value(1))
                .andExpect(jsonPath("$.tamanho_pagina").value(2))
                .andExpect(jsonPath("$.total_elementos").value(5))
                .andExpect(jsonPath("$.total_paginas").value(3));
    }

    private void persistAnalysis(EnergyCategory categoria, int score, LocalDateTime createdAt) {
        EnergyAnalysisEntity analysis = EnergyAnalysisEntity.builder()
                .consumoKwh(420.0)
                .usoHorarioPico(true)
                .quantidadeEquipamentos(10)
                .tipoImovel(PropertyType.CASA)
                .horasAltoConsumo(8)
                .categoria(categoria)
                .probabilidade(0.95)
                .score(score)
                .custoEstimadoMensal(new BigDecimal("315.00"))
                .fonteClassificacao(ClassificationSource.RULE_BASED)
                .recomendacoes(List.of("Dica 1"))
                .build();

        EnergyAnalysisEntity savedAnalysis = energyAnalysisRepository.saveAndFlush(analysis);
        jdbcTemplate.update(
                "UPDATE energy_analysis SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(createdAt),
                savedAnalysis.getId()
        );
    }
}
