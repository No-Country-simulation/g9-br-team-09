package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RuleBasedEnergyClassifier implements EnergyClassifier {

    private static final double MAX_PROBABILITY = 1.0;
    private static final double SCORE_TO_PROBABILITY_DIVISOR = 100.0;

    private static final int HIGH_CONSUMPTION_THRESHOLD = 400;
    private static final int HIGH_CONSUMPTION_SCORE = 50;
    private static final int PEAK_HOURS_SCORE = 15;
    private static final int MANY_DEVICES_THRESHOLD = 8;
    private static final int MANY_DEVICES_SCORE = 10;
    private static final int HIGH_USAGE_HOURS_THRESHOLD = 6;
    private static final int HIGH_USAGE_HOURS_SCORE = 15;
    private static final int HOUSE_SCORE = 5;
    private static final int COMMERCIAL_SCORE = 10;

    private static final int EFFICIENT_MAX_SCORE = 30;
    private static final int MODERATE_MAX_SCORE = 60;

    @Override
    public EnergyAnalysisResponse classify(EnergyAnalysisRequest request) {
        int score = calculateScore(request);
        EnergyCategory categoria = determineCategory(score);

        double probabilidade = estimateProbability(score);

        return new EnergyAnalysisResponse(
                categoria,
                probabilidade,
                score,
                null,
                List.of(),
                ClassificationSource.RULE_BASED
        );
    }

    int calculateScore(EnergyAnalysisRequest request) {
        int score = 0;

        if (request.consumoKwh() != null && request.consumoKwh() > HIGH_CONSUMPTION_THRESHOLD) {
            score += HIGH_CONSUMPTION_SCORE;
        }

        if (Boolean.TRUE.equals(request.usoHorarioPico())) {
            score += PEAK_HOURS_SCORE;
        }

        if (request.quantidadeEquipamentos() != null
                && request.quantidadeEquipamentos() > MANY_DEVICES_THRESHOLD) {
            score += MANY_DEVICES_SCORE;
        }

        if (request.horasAltoConsumo() != null
                && request.horasAltoConsumo() > HIGH_USAGE_HOURS_THRESHOLD) {
            score += HIGH_USAGE_HOURS_SCORE;
        }

        if (request.tipoImovel() == PropertyType.CASA) {
            score += HOUSE_SCORE;
        } else if (request.tipoImovel() == PropertyType.COMERCIO) {
            score += COMMERCIAL_SCORE;
        }

        return score;
    }

    double estimateProbability(int score) {
        return Math.min(score / SCORE_TO_PROBABILITY_DIVISOR, MAX_PROBABILITY);
    }

    EnergyCategory determineCategory(int score) {
        if (score <= EFFICIENT_MAX_SCORE) {
            return EnergyCategory.EFICIENTE;
        }
        if (score <= MODERATE_MAX_SCORE) {
            return EnergyCategory.MODERADO;
        }
        return EnergyCategory.INEFICIENTE;
    }
}
