package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisDashboardResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisDetailResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisListResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisSummaryResponse;
import br.com.g9.energiai.backend.entity.AppUser;
import br.com.g9.energiai.backend.entity.EnergyAnalysisEntity;
import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import br.com.g9.energiai.backend.exception.ResourceNotFoundException;
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
import org.springframework.security.authentication.BadCredentialsException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnergyAnalysisServiceTest {

    private static final String ANALYSIS_NOT_FOUND_MESSAGE = "Análise não encontrada com o ID informado.";

    @Mock
    private EnergyAnalysisOrchestrator energyAnalysisOrchestrator;

    @Mock
    private EnergyCostCalculator energyCostCalculator;

    @Mock
    private EnergyAnalysisRepository energyAnalysisRepository;

    @Mock
    private EnergyAnalysisMapper energyAnalysisMapper;

    @Mock
    private AuthenticatedUserProvider authenticatedUserProvider;

    @InjectMocks
    private EnergyAnalysisService energyAnalysisService;


    @Test
    @DisplayName("Deve orquestrar o fluxo de análise e persistência utilizando mocks")
    void shouldOrchestrateAnalysisAndPersistence() {
        AppUser currentUser = AppUser.builder().id(1L).build();
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(500.0, true, 10, PropertyType.CASA, 8);

        EnergyAnalysisResult analysisResult = new EnergyAnalysisResult(
                EnergyCategory.INEFICIENTE, 0.95, 95, List.of("Dica 1"), ClassificationSource.ML_MODEL
        );
        BigDecimal cost = new BigDecimal("375.00");
        EnergyAnalysisEntity entity = new EnergyAnalysisEntity();
        EnergyAnalysisEntity savedEntity = new EnergyAnalysisEntity();
        savedEntity.setId(1L);

        EnergyAnalysisResponse expectedResponse = new EnergyAnalysisResponse(
                1L, EnergyCategory.INEFICIENTE, 0.95, 95, cost, analysisResult.recomendacoes(), ClassificationSource.ML_MODEL
        );

        when(authenticatedUserProvider.getCurrentUser()).thenReturn(currentUser);
        when(energyAnalysisOrchestrator.analyze(request)).thenReturn(analysisResult);
        when(energyCostCalculator.calculate(request.consumoKwh())).thenReturn(cost);
        when(energyAnalysisMapper.toEntity(request, analysisResult, cost, currentUser)).thenReturn(entity);
        when(energyAnalysisRepository.save(entity)).thenReturn(savedEntity);
        when(energyAnalysisMapper.toResponse(savedEntity)).thenReturn(expectedResponse);

        EnergyAnalysisResponse response = energyAnalysisService.analyze(request);

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals(EnergyCategory.INEFICIENTE, response.categoria());
        assertEquals(cost, response.custoEstimadoMensal());

        verify(energyAnalysisOrchestrator).analyze(request);
        verify(energyCostCalculator).calculate(request.consumoKwh());
        verify(energyAnalysisMapper).toEntity(request, analysisResult, cost, currentUser);
        verify(energyAnalysisRepository).save(entity);
        verify(energyAnalysisMapper).toResponse(savedEntity);
    }

    @Test
    @DisplayName("Não deve calcular nem persistir análise quando o usuário autenticado for inválido")
    void shouldNotAnalyzeOrPersistWhenAuthenticatedUserIsInvalid() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(500.0, true, 10, PropertyType.CASA, 8);
        when(authenticatedUserProvider.getCurrentUser())
                .thenThrow(new BadCredentialsException("Token inválido ou usuário não autorizado"));

        assertThrows(BadCredentialsException.class, () -> energyAnalysisService.analyze(request));

        verify(energyAnalysisOrchestrator, never()).analyze(any());
        verify(energyAnalysisRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve propagar falha inesperada durante a persistência")
    void shouldPropagateUnexpectedPersistenceFailure() {
        AppUser currentUser = AppUser.builder().id(1L).build();
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(500.0, true, 10, PropertyType.CASA, 8);
        EnergyAnalysisResult analysisResult = new EnergyAnalysisResult(
                EnergyCategory.INEFICIENTE, 0.95, 95, List.of("Dica"), ClassificationSource.ML_MODEL
        );
        BigDecimal cost = new BigDecimal("375.00");
        EnergyAnalysisEntity entity = new EnergyAnalysisEntity();
        RuntimeException expected = new RuntimeException("Falha inesperada na persistência");

        when(authenticatedUserProvider.getCurrentUser()).thenReturn(currentUser);
        when(energyAnalysisOrchestrator.analyze(request)).thenReturn(analysisResult);
        when(energyCostCalculator.calculate(request.consumoKwh())).thenReturn(cost);
        when(energyAnalysisMapper.toEntity(request, analysisResult, cost, currentUser)).thenReturn(entity);
        when(energyAnalysisRepository.save(entity)).thenThrow(expected);

        RuntimeException actual = assertThrows(RuntimeException.class, () -> energyAnalysisService.analyze(request));

        assertSame(expected, actual);
        verify(energyAnalysisMapper, never()).toResponse(any(EnergyAnalysisEntity.class));
    }

    @Test
    @DisplayName("Deve retornar histórico de análises paginado com sucesso")
    void shouldReturnPaginatedHistorySuccessfully() {
        AppUser currentUser = AppUser.builder().id(7L).build();
        Pageable pageable = PageRequest.of(0, 10);
        EnergyAnalysisEntity entity = new EnergyAnalysisEntity();
        Page<EnergyAnalysisEntity> page = new PageImpl<>(List.of(entity));

        EnergyAnalysisSummaryResponse summary = new EnergyAnalysisSummaryResponse(
                1L, EnergyCategory.EFICIENTE, 0.1, 10, new BigDecimal("75.00"),
                LocalDateTime.of(2026, 7, 14, 18, 0)
        );

        when(authenticatedUserProvider.getCurrentUser()).thenReturn(currentUser);
        when(energyAnalysisRepository.findAllByUserIdOrderByCreatedAtDesc(currentUser.getId(), pageable)).thenReturn(page);
        when(energyAnalysisMapper.toSummaryResponse(any(EnergyAnalysisEntity.class))).thenReturn(summary);

        EnergyAnalysisListResponse response = energyAnalysisService.findAll(pageable);

        assertNotNull(response);
        assertEquals(1, response.analises().size());
        assertEquals(0, response.paginaAtual());
        assertEquals(1, response.totalPaginas());
        assertEquals(1L, response.totalElementos());

        verify(energyAnalysisRepository).findAllByUserIdOrderByCreatedAtDesc(currentUser.getId(), pageable);
        verify(energyAnalysisMapper).toSummaryResponse(entity);
    }

    @Test
    @DisplayName("Deve retornar detalhes quando a análise pertencer ao usuário autenticado")
    void shouldReturnDetailWhenAnalysisExists() {
        AppUser currentUser = AppUser.builder().id(7L).build();
        EnergyAnalysisEntity entity = new EnergyAnalysisEntity();
        entity.setId(1L);
        EnergyAnalysisDetailResponse expected = new EnergyAnalysisDetailResponse(
                1L, 100.0, false, 2, PropertyType.CASA, 2, EnergyCategory.EFICIENTE,
                0.1, 10, new BigDecimal("75.00"), List.of(), ClassificationSource.RULE_BASED,
                LocalDateTime.of(2026, 7, 14, 18, 0)
        );

        when(authenticatedUserProvider.getCurrentUser()).thenReturn(currentUser);
        when(energyAnalysisRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(entity));
        when(energyAnalysisMapper.toDetailResponse(entity)).thenReturn(expected);

        EnergyAnalysisDetailResponse response = energyAnalysisService.findById(1L);

        assertSame(expected, response);
        verify(energyAnalysisRepository).findByIdAndUserId(1L, currentUser.getId());
        verify(energyAnalysisMapper).toDetailResponse(entity);
    }

    @Test
    @DisplayName("Deve lançar exceção com mensagem genérica quando o ID não existir para o usuário atual")
    void shouldThrowWhenAnalysisDoesNotExist() {
        AppUser currentUser = AppUser.builder().id(7L).build();

        when(authenticatedUserProvider.getCurrentUser()).thenReturn(currentUser);
        when(energyAnalysisRepository.findByIdAndUserId(99L,currentUser.getId())).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> energyAnalysisService.findById(99L));

        assertEquals(ANALYSIS_NOT_FOUND_MESSAGE, exception.getMessage());
        verify(energyAnalysisRepository).findByIdAndUserId(99L, currentUser.getId());
        verify(energyAnalysisMapper, never()).toDetailResponse(any(EnergyAnalysisEntity.class));
    }

    @Test
    @DisplayName("Deve retornar resumo do dashboard com média de custo arredondada")
    void shouldReturnDashboardSummaryWithRoundedCostAverage() {
        AppUser currentUser = AppUser.builder().id(7L).build();

        when(authenticatedUserProvider.getCurrentUser()).thenReturn(currentUser);
        when(energyAnalysisRepository.countByUserId(currentUser.getId())).thenReturn(3L);
        when(energyAnalysisRepository.getAverageConsumoKwhByUserId(currentUser.getId())).thenReturn(420.5);
        when(energyAnalysisRepository.getTotalMonthlyCostByUserId(currentUser.getId())).thenReturn(new BigDecimal("100.00"));
        when(energyAnalysisRepository.countByUserIdAndCategoria(currentUser.getId(), EnergyCategory.EFICIENTE)).thenReturn(1L);
        when(energyAnalysisRepository.countByUserIdAndCategoria(currentUser.getId(), EnergyCategory.MODERADO)).thenReturn(1L);
        when(energyAnalysisRepository.countByUserIdAndCategoria(currentUser.getId(), EnergyCategory.INEFICIENTE)).thenReturn(1L);

        EnergyAnalysisDashboardResponse response = energyAnalysisService.getDashboardSummary();

        assertEquals(3L, response.totalAnalises());
        assertEquals(420.5, response.mediaConsumoKwh());
        assertEquals(new BigDecimal("33.33"), response.mediaCustoMensal());
        assertEquals(1L, response.totalEficiente());
        assertEquals(1L, response.totalModerado());
        assertEquals(1L, response.totalIneficiente());
    }

    @Test
    @DisplayName("Deve retornar zeros quando não houver análises no dashboard")
    void shouldReturnZeroedDashboardSummaryWhenThereAreNoAnalyses() {
        AppUser currentUser = AppUser.builder().id(7L).build();

        when(authenticatedUserProvider.getCurrentUser()).thenReturn(currentUser);
        when(energyAnalysisRepository.countByUserId(currentUser.getId())).thenReturn(0L);
        when(energyAnalysisRepository.getAverageConsumoKwhByUserId(currentUser.getId())).thenReturn(null);
        when(energyAnalysisRepository.getTotalMonthlyCostByUserId(currentUser.getId())).thenReturn(null);
        when(energyAnalysisRepository.countByUserIdAndCategoria(currentUser.getId(), EnergyCategory.EFICIENTE)).thenReturn(0L);
        when(energyAnalysisRepository.countByUserIdAndCategoria(currentUser.getId(), EnergyCategory.MODERADO)).thenReturn(0L);
        when(energyAnalysisRepository.countByUserIdAndCategoria(currentUser.getId(), EnergyCategory.INEFICIENTE)).thenReturn(0L);

        EnergyAnalysisDashboardResponse response = energyAnalysisService.getDashboardSummary();

        assertEquals(0L, response.totalAnalises());
        assertEquals(0.0, response.mediaConsumoKwh());
        assertEquals(new BigDecimal("0.00"), response.mediaCustoMensal());
        assertEquals(0L, response.totalEficiente());
        assertEquals(0L, response.totalModerado());
        assertEquals(0L, response.totalIneficiente());
    }
}
