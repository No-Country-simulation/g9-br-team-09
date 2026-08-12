package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.client.ml.MlPredictionClient;
import br.com.g9.energiai.backend.client.ml.dto.MlPredictionRequest;
import br.com.g9.energiai.backend.client.ml.dto.MlPredictionResponse;
import br.com.g9.energiai.backend.client.ml.exception.MlPredictionClientException;
import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.enums.ClassificationSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class EnergyAnalysisOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnergyAnalysisOrchestrator.class);

    private final MlPredictionClient mlPredictionClient;
    private final EnergyClassifier energyClassifier;
    private final EnergyRecommendationService energyRecommendationService;

    public EnergyAnalysisOrchestrator(MlPredictionClient mlPredictionClient,
                                      EnergyClassifier energyClassifier,
                                      EnergyRecommendationService energyRecommendationService) {
        this.mlPredictionClient = mlPredictionClient;
        this.energyClassifier = energyClassifier;
        this.energyRecommendationService = energyRecommendationService;
    }

    public EnergyAnalysisResult analyze(EnergyAnalysisRequest request) {
        try {
            MlPredictionResponse prediction = mlPredictionClient.predict(toMlPredictionRequest(request));
            validatePrediction(prediction);

            return new EnergyAnalysisResult(
                    prediction.categoria(),
                    prediction.probabilidade(),
                    prediction.score(),
                    prediction.recomendacoes(),
                    ClassificationSource.ML_MODEL
            );
        } catch (MlPredictionClientException | InvalidMlPredictionResponseException exception) {
            LOGGER.warn("Fallback local acionado após falha de ML ({}): {}",
                    exception.getClass().getSimpleName(), exception.getMessage());
            return analyzeWithFallback(request);
        }
    }

    private EnergyAnalysisResult analyzeWithFallback(EnergyAnalysisRequest request) {
        EnergyAnalysisResponse classification = energyClassifier.classify(request);
        List<String> recommendations = energyRecommendationService.generate(request, classification.categoria());

        return new EnergyAnalysisResult(
                classification.categoria(),
                classification.probabilidade(),
                classification.score(),
                recommendations,
                ClassificationSource.RULE_BASED_FALLBACK
        );
    }

    private MlPredictionRequest toMlPredictionRequest(EnergyAnalysisRequest request) {
        return new MlPredictionRequest(
                request.consumoKwh(),
                request.usoHorarioPico(),
                request.quantidadeEquipamentos(),
                request.tipoImovel(),
                request.horasAltoConsumo()
        );
    }

    private void validatePrediction(MlPredictionResponse prediction) {
        if (prediction == null
                || prediction.categoria() == null
                || prediction.probabilidade() == null
                || !Double.isFinite(prediction.probabilidade())
                || prediction.probabilidade() < 0
                || prediction.probabilidade() > 1
                || prediction.score() == null
                || prediction.score() < 0
                || prediction.score() > 100
                || prediction.recomendacoes() == null
                || prediction.recomendacoes().isEmpty()
                || prediction.recomendacoes().stream().anyMatch(Objects::isNull)) {
            throw new InvalidMlPredictionResponseException("Resposta da API de ML inválida");
        }
    }

    private static class InvalidMlPredictionResponseException extends RuntimeException {

        private InvalidMlPredictionResponseException(String message) {
            super(message);
        }
    }
}
