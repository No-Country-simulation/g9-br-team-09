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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class EnergyAnalysisListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EnergyAnalysisRepository energyAnalysisRepository;

    @BeforeEach
    void setup() {
        energyAnalysisRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve listar histórico de análises com sucesso e retornar 200 OK")
    void shouldListHistorySuccessfully() throws Exception {
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

        energyAnalysisRepository.save(analysis);

        mockMvc.perform(get("/api/v1/analise-energetica").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.analises").isArray())
                .andExpect(jsonPath("$.analises[0].categoria").value("INEFICIENTE"))
                .andExpect(jsonPath("$.analises[0].score").value(95));
    }
}
