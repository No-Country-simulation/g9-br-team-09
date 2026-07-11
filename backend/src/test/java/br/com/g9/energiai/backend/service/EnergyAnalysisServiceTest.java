package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.entity.EnergyAnalysisEntity;
import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import br.com.g9.energiai.backend.mapper.EnergyAnalysisMapper;
import br.com.g9.energiai.backend.repository.EnergyAnalysisRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnergyAnalysisServiceTest {

    @Test
    @DisplayName("Deve orquestrar classificação, custo e recomendações preservando todos os campos da resposta")
    void shouldAssembleFinalResponseFromCollaborators() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(
                500.0,
                true,
                10,
                PropertyType.CASA,
                8
        );
        EnergyAnalysisResponse classification = new EnergyAnalysisResponse(
                null,
                EnergyCategory.INEFICIENTE,
                0.95,
                95,
                null,
                List.of(),
                ClassificationSource.RULE_BASED
        );
        BigDecimal estimatedCost = new BigDecimal("375.00");
        List<String> recommendations = List.of(
                "Reduzir o uso de equipamentos durante horários de pico.",
                "Avaliar equipamentos com alto consumo energético."
        );

        StubEnergyClassifier energyClassifier = new StubEnergyClassifier(classification);
        StubEnergyCostCalculator energyCostCalculator = new StubEnergyCostCalculator(estimatedCost);
        CapturingEnergyRecommendationService energyRecommendationService =
                new CapturingEnergyRecommendationService(recommendations);
        EnergyAnalysisRepository energyAnalysisRepository = mock(EnergyAnalysisRepository.class);
        EnergyAnalysisMapper energyAnalysisMapper = new EnergyAnalysisMapper();

        org.mockito.ArgumentCaptor<EnergyAnalysisEntity> entityCaptor =
                org.mockito.ArgumentCaptor.forClass(EnergyAnalysisEntity.class);

        when(energyAnalysisRepository.save(any(EnergyAnalysisEntity.class)))
                .thenAnswer(invocation -> {
                    EnergyAnalysisEntity entity = invocation.getArgument(0);
                    entity.setId(1L);
                    return entity;
                });

        EnergyAnalysisService service = new EnergyAnalysisService(
                energyClassifier,
                energyCostCalculator,
                energyRecommendationService,
                energyAnalysisRepository,
                energyAnalysisMapper
        );

        EnergyAnalysisResponse response = service.analyze(request);

        assertEquals(EnergyCategory.INEFICIENTE, response.categoria());
        assertEquals(0.95, response.probabilidade());
        assertEquals(95, response.score());
        assertEquals(ClassificationSource.RULE_BASED, response.fonteClassificacao());
        assertEquals(estimatedCost, response.custoEstimadoMensal());
        assertEquals(recommendations, response.recomendacoes());
        assertEquals(1L, response.id());

        assertSame(request, energyClassifier.receivedRequest);
        assertEquals(500.0, energyCostCalculator.receivedConsumption);
        assertSame(request, energyRecommendationService.receivedRequest);
        assertEquals(EnergyCategory.INEFICIENTE, energyRecommendationService.receivedCategory);
        verify(energyAnalysisRepository).save(entityCaptor.capture());

        EnergyAnalysisEntity persistedEntity = entityCaptor.getValue();
        assertEquals(500.0, persistedEntity.getConsumoKwh());
        assertEquals(Boolean.TRUE, persistedEntity.getUsoHorarioPico());
        assertEquals(10, persistedEntity.getQuantidadeEquipamentos());
        assertEquals(PropertyType.CASA, persistedEntity.getTipoImovel());
        assertEquals(8, persistedEntity.getHorasAltoConsumo());
        assertEquals(EnergyCategory.INEFICIENTE, persistedEntity.getCategoria());
        assertEquals(0.95, persistedEntity.getProbabilidade());
        assertEquals(95, persistedEntity.getScore());
        assertEquals(estimatedCost, persistedEntity.getCustoEstimadoMensal());
        assertIterableEquals(recommendations, persistedEntity.getRecomendacoes());
        assertEquals(ClassificationSource.RULE_BASED, persistedEntity.getFonteClassificacao());
    }

    private static final class StubEnergyClassifier implements EnergyClassifier {
        private final EnergyAnalysisResponse response;
        private EnergyAnalysisRequest receivedRequest;

        private StubEnergyClassifier(EnergyAnalysisResponse response) {
            this.response = response;
        }

        @Override
        public EnergyAnalysisResponse classify(EnergyAnalysisRequest request) {
            this.receivedRequest = request;
            return response;
        }
    }

    private static final class StubEnergyCostCalculator extends EnergyCostCalculator {
        private final BigDecimal result;
        private Double receivedConsumption;

        private StubEnergyCostCalculator(BigDecimal result) {
            super(BigDecimal.ONE);
            this.result = result;
        }

        @Override
        public BigDecimal calculate(Double consumptionKwh) {
            this.receivedConsumption = consumptionKwh;
            return result;
        }
    }

    private static final class CapturingEnergyRecommendationService implements EnergyRecommendationService {
        private final List<String> result;
        private EnergyAnalysisRequest receivedRequest;
        private EnergyCategory receivedCategory;

        private CapturingEnergyRecommendationService(List<String> result) {
            this.result = result;
        }

        @Override
        public List<String> generate(EnergyAnalysisRequest request, EnergyCategory categoria) {
            this.receivedRequest = request;
            this.receivedCategory = categoria;
            return result;
        }
    }

}
