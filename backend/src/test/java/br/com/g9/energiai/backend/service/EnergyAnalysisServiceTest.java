package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisListResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisSummaryResponse;
import br.com.g9.energiai.backend.entity.EnergyAnalysisEntity;
import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import br.com.g9.energiai.backend.mapper.EnergyAnalysisMapper;
import br.com.g9.energiai.backend.repository.EnergyAnalysisRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnergyAnalysisServiceTest {

    @Mock
    private EnergyClassifier energyClassifier;

    @Mock
    private EnergyCostCalculator energyCostCalculator;

    @Mock
    private EnergyRecommendationService energyRecommendationService;

    @Mock
    private EnergyAnalysisRepository energyAnalysisRepository;

    @Mock
    private EnergyAnalysisMapper energyAnalysisMapper;

    @InjectMocks
    private EnergyAnalysisService energyAnalysisService;

    @Test
    @DisplayName("Deve orquestrar o fluxo de análise e persistência utilizando mocks")
    void shouldOrchestrateAnalysisAndPersistence() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(500.0, true, 10, PropertyType.CASA, 8);

        EnergyAnalysisResponse classification = new EnergyAnalysisResponse(
                null, EnergyCategory.INEFICIENTE, 0.95, 95, null, List.of(), ClassificationSource.RULE_BASED
        );

        BigDecimal cost = new BigDecimal("375.00");
        List<String> recommendations = List.of("Dica 1");
        EnergyAnalysisEntity entity = new EnergyAnalysisEntity();
        EnergyAnalysisEntity savedEntity = new EnergyAnalysisEntity();
        savedEntity.setId(1L);

        EnergyAnalysisResponse expectedResponse = new EnergyAnalysisResponse(
                1L, EnergyCategory.INEFICIENTE, 0.95, 95, cost, recommendations, ClassificationSource.RULE_BASED
        );

        when(energyClassifier.classify(request)).thenReturn(classification);
        when(energyCostCalculator.calculate(request.consumoKwh())).thenReturn(cost);
        when(energyRecommendationService.generate(request, EnergyCategory.INEFICIENTE)).thenReturn(recommendations);
        when(energyAnalysisMapper.toEntity(request, classification, cost, recommendations)).thenReturn(entity);
        when(energyAnalysisRepository.save(entity)).thenReturn(savedEntity);
        when(energyAnalysisMapper.toResponse(savedEntity)).thenReturn(expectedResponse);

        EnergyAnalysisResponse response = energyAnalysisService.analyze(request);

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals(EnergyCategory.INEFICIENTE, response.categoria());
        assertEquals(cost, response.custoEstimadoMensal());

        verify(energyClassifier).classify(request);
        verify(energyAnalysisRepository).save(entity);
    }

    @Test
    @DisplayName("Deve retornar histórico de análises paginado com sucesso")
    void shouldReturnPaginatedHistorySuccessfully() {
        Pageable pageable = PageRequest.of(0, 10);
        EnergyAnalysisEntity entity = new EnergyAnalysisEntity();
        Page<EnergyAnalysisEntity> page = new PageImpl<>(List.of(entity));

        EnergyAnalysisSummaryResponse summary = new EnergyAnalysisSummaryResponse(
                1L, EnergyCategory.EFICIENTE, 0.1, 10, new BigDecimal("75.00"), LocalDateTime.now()
        );

        when(energyAnalysisRepository.findAll(pageable)).thenReturn(page);
        when(energyAnalysisMapper.toSummaryResponse(any(EnergyAnalysisEntity.class))).thenReturn(summary);

        EnergyAnalysisListResponse response = energyAnalysisService.findAll(pageable);

        assertNotNull(response);
        assertEquals(1, response.analises().size());
        assertEquals(0, response.paginaAtual());
        assertEquals(1, response.totalPaginas());
        assertEquals(1L, response.totalElementos());

        verify(energyAnalysisRepository).findAll(pageable);
        verify(energyAnalysisMapper).toSummaryResponse(entity);
    }
}
