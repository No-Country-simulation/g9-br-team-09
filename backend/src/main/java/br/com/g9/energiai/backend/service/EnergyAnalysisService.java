package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EnergyAnalysisService {

    private final EnergyClassifier energyClassifier;
    private final EnergyCostCalculator energyCostCalculator;
    private final EnergyRecommendationService energyRecommendationService;

    public EnergyAnalysisResponse analyze(EnergyAnalysisRequest request) {
        EnergyAnalysisResponse classification = energyClassifier.classify(request);
        BigDecimal estimatedCost = energyCostCalculator.calculate(request.consumoKwh());
        List<String> recommendations = energyRecommendationService.generate(
            request,
            classification.categoria()
        );

        return new EnergyAnalysisResponse(
            classification.categoria(),
            classification.probabilidade(),
            classification.score(),
            estimatedCost,
            recommendations,
            classification.fonteClassificacao()
        );
    }
}
