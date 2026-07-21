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
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EnergyAnalysisService {

    private final EnergyAnalysisOrchestrator energyAnalysisOrchestrator;
    private final EnergyCostCalculator energyCostCalculator;
    private final EnergyAnalysisRepository energyAnalysisRepository;
    private final EnergyAnalysisMapper energyAnalysisMapper;

    @Transactional
    public EnergyAnalysisResponse analyze(EnergyAnalysisRequest request) {
        EnergyAnalysisResult analysisResult = energyAnalysisOrchestrator.analyze(request);
        EnergyAnalysisResponse classification = new EnergyAnalysisResponse(
                null,
                analysisResult.categoria(),
                analysisResult.probabilidade(),
                analysisResult.score(),
                null,
                List.of(),
                analysisResult.fonteClassificacao()
        );
        BigDecimal estimatedCost = energyCostCalculator.calculate(request.consumoKwh());

        EnergyAnalysisEntity entity = energyAnalysisMapper.toEntity(
                request, classification, estimatedCost, analysisResult.recomendacoes()
        );
        EnergyAnalysisEntity savedEntity = energyAnalysisRepository.save(entity);

        return energyAnalysisMapper.toResponse(savedEntity);
    }

    @Transactional(readOnly = true)
    public EnergyAnalysisListResponse findAll(Pageable pageable) {
        Page<EnergyAnalysisEntity> analysisPage = energyAnalysisRepository.findAll(pageable);

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
        return energyAnalysisRepository.findById(id)
                .map(energyAnalysisMapper::toDetailResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Análise não encontrada com o ID: " + id));
    }

    @Transactional(readOnly = true)
    public EnergyAnalysisDashboardResponse getDashboardSummary() {
        long total = energyAnalysisRepository.count();

        Double mediaConsumo = Optional.ofNullable(energyAnalysisRepository.getAverageConsumoKwh())
                .orElse(0.0);

        BigDecimal totalCusto = Optional.ofNullable(energyAnalysisRepository.getTotalCustoMensal())
                .orElse(BigDecimal.ZERO);

        BigDecimal mediaCusto = total == 0
                ? BigDecimal.ZERO.setScale(2)
                : totalCusto.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        return new EnergyAnalysisDashboardResponse(
                total,
                mediaConsumo,
                mediaCusto,
                energyAnalysisRepository.countByCategoria(EnergyCategory.EFICIENTE),
                energyAnalysisRepository.countByCategoria(EnergyCategory.MODERADO),
                energyAnalysisRepository.countByCategoria(EnergyCategory.INEFICIENTE)
        );
    }
}
