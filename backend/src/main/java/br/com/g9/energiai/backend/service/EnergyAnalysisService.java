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

    /**
     * Executa o fluxo completo de análise: Classificação, Cálculo de Custo e Recomendações.
     */
    public EnergyAnalysisResponse analyze(EnergyAnalysisRequest request) {

        EnergyAnalysisResponse partialResponse = energyClassifier.classify(request);

        BigDecimal estimatedCost = energyCostCalculator.calculate(request.consumoKwh());

        List<String> recommendations = energyRecommendationService.generate(request, partialResponse.categoria());

        return new EnergyAnalysisResponse(
                partialResponse.categoria(),
                partialResponse.probabilidade(),
                partialResponse.score(),
                estimatedCost,
                recommendations,
                partialResponse.fonteClassificacao()
        );
    }
}
