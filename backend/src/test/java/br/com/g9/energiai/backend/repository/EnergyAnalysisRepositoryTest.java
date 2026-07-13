package br.com.g9.energiai.backend.repository;

import br.com.g9.energiai.backend.entity.EnergyAnalysisEntity;
import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("local")
class EnergyAnalysisRepositoryTest {

    @Autowired
    private EnergyAnalysisRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Deve persistir e recuperar a análise usando apenas a tabela energy_analysis")
    void shouldPersistAndLoadAnalysisUsingSingleTable() {
        List<String> recommendations = List.of(
            "Reduzir o uso de equipamentos durante horários de pico.",
            "Avaliar equipamentos com alto consumo energético."
        );

        EnergyAnalysisEntity entity = EnergyAnalysisEntity.builder()
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
            .recomendacoes(recommendations)
            .build();

        EnergyAnalysisEntity saved = repository.saveAndFlush(entity);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());

        String rawRecommendations = jdbcTemplate.queryForObject(
            "select recomendacoes from energy_analysis where id = ?",
            String.class,
            saved.getId()
        );
        Integer domainTableCount = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where upper(table_name) = 'ENERGY_ANALYSIS'",
            Integer.class
        );
        Integer recommendationsTableCount = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where upper(table_name) = 'ENERGY_ANALYSIS_RECOMMENDATIONS'",
            Integer.class
        );

        entityManager.clear();

        EnergyAnalysisEntity reloaded = repository.findById(saved.getId()).orElseThrow();

        assertEquals(1, domainTableCount);
        assertEquals(0, recommendationsTableCount);
        assertEquals("[\"Reduzir o uso de equipamentos durante horários de pico.\",\"Avaliar equipamentos com alto consumo energético.\"]", rawRecommendations);
        assertEquals(PropertyType.CASA, reloaded.getTipoImovel());
        assertEquals(EnergyCategory.INEFICIENTE, reloaded.getCategoria());
        assertEquals(ClassificationSource.RULE_BASED, reloaded.getFonteClassificacao());
        assertEquals(new BigDecimal("315.00"), reloaded.getCustoEstimadoMensal());
        assertEquals(recommendations, reloaded.getRecomendacoes());
        assertEquals(saved.getId(), reloaded.getId());
        assertNotNull(reloaded.getCreatedAt());
        assertTrue(reloaded.getCreatedAt().isEqual(saved.getCreatedAt()) || reloaded.getCreatedAt().isAfter(saved.getCreatedAt()));
    }
}
