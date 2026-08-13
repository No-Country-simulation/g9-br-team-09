package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisDashboardResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisDetailResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisListResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.entity.EnergyAnalysisEntity;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.exception.ResourceNotFoundException;
import br.com.g9.energiai.backend.mapper.EnergyAnalysisMapper;
import br.com.g9.energiai.backend.repository.EnergyAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EnergyAnalysisService {

    private static final String ANALYSIS_NOT_FOUND_MESSAGE = "Análise não encontrada com o ID informado.";
    private final EnergyAnalysisOrchestrator energyAnalysisOrchestrator;
    private final EnergyCostCalculator energyCostCalculator;
    private final EnergyAnalysisRepository energyAnalysisRepository;
    private final EnergyAnalysisMapper energyAnalysisMapper;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public EnergyAnalysisResponse analyze(EnergyAnalysisRequest request) {
        var currentUser = authenticatedUserProvider.getCurrentUser();

        EnergyAnalysisResult analysisResult = energyAnalysisOrchestrator.analyze(request);
        BigDecimal estimatedCost = energyCostCalculator.calculate(request.consumoKwh());

        EnergyAnalysisEntity entity = energyAnalysisMapper.toEntity(
                request, analysisResult, estimatedCost, currentUser
        );
        EnergyAnalysisEntity savedEntity = energyAnalysisRepository.save(entity);

        return energyAnalysisMapper.toResponse(savedEntity);
    }

    @Transactional(readOnly = true)
    public EnergyAnalysisListResponse findAll(Pageable pageable) {
        var currentUser = authenticatedUserProvider.getCurrentUser();

        Page<EnergyAnalysisEntity> analysisPage = energyAnalysisRepository
                .findAllByUserIdOrderByCreatedAtDesc(currentUser.getId(), pageable);

        var summaries = analysisPage.getContent().stream()
                .map(energyAnalysisMapper::toSummaryResponse)
                .toList();

        return new EnergyAnalysisListResponse(
                summaries,
                analysisPage.getNumber(),
                analysisPage.getSize(),
                analysisPage.getTotalElements(),
                analysisPage.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public EnergyAnalysisDetailResponse findById(Long id) {
        var currentUser = authenticatedUserProvider.getCurrentUser();

        return energyAnalysisRepository.findByIdAndUserId(id, currentUser.getId())
                .map(energyAnalysisMapper::toDetailResponse)
                .orElseThrow(() -> new ResourceNotFoundException(ANALYSIS_NOT_FOUND_MESSAGE));
    }

    @Transactional(readOnly = true)
    public EnergyAnalysisDashboardResponse getDashboardSummary() {
        var currentUser = authenticatedUserProvider.getCurrentUser();
        var userId = currentUser.getId();

        long total = energyAnalysisRepository.countByUserId(userId);

        Double mediaConsumo = Optional.ofNullable(energyAnalysisRepository.getAverageConsumoKwhByUserId(userId))
                .orElse(0.0);

        BigDecimal totalCusto = Optional.ofNullable(energyAnalysisRepository.getTotalMonthlyCostByUserId(userId))
                .orElse(BigDecimal.ZERO);

        BigDecimal mediaCusto = total == 0
                ? BigDecimal.ZERO.setScale(2)
                : totalCusto.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        return new EnergyAnalysisDashboardResponse(
                total,
                mediaConsumo,
                mediaCusto,
                energyAnalysisRepository.countByUserIdAndCategoria(userId, EnergyCategory.EFICIENTE),
                energyAnalysisRepository.countByUserIdAndCategoria(userId, EnergyCategory.MODERADO),
                energyAnalysisRepository.countByUserIdAndCategoria(userId, EnergyCategory.INEFICIENTE)
        );
    }
}
