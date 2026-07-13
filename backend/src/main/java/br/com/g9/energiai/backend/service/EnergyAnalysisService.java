package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.entity.EnergyAnalysisEntity;
import br.com.g9.energiai.backend.mapper.EnergyAnalysisMapper;
import br.com.g9.energiai.backend.repository.EnergyAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EnergyAnalysisService {

    private final EnergyClassifier energyClassifier;
    private final EnergyCostCalculator energyCostCalculator;
    private final EnergyRecommendationService energyRecommendationService;
    private final EnergyAnalysisRepository energyAnalysisRepository;
    private final EnergyAnalysisMapper energyAnalysisMapper;

    @Transactional
    public EnergyAnalysisResponse analyze(EnergyAnalysisRequest request) {
        EnergyAnalysisResponse classification = energyClassifier.classify(request);

        BigDecimal estimatedCost = energyCostCalculator.calculate(request.consumoKwh());

        List<String> recommendations = energyRecommendationService.generate(
                request,
                classification.categoria()
        );

        EnergyAnalysisEntity entity = energyAnalysisMapper.toEntity(
                request,
                classification,
                estimatedCost,
                recommendations
        );

        EnergyAnalysisEntity savedEntity = energyAnalysisRepository.save(entity);

        return energyAnalysisMapper.toResponse(savedEntity);
    }
}
