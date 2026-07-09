package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class RuleBasedEnergyClassifier implements EnergyClassifier {

    @Override
    public EnergyAnalysisResponse classify(EnergyAnalysisRequest request) {
        int score = calculateScore(request);
        EnergyCategory categoria = determineCategory(score);

        double probabilidade = Math.min(score / 100.0, 1.0);

        return new EnergyAnalysisResponse(
                categoria,
                probabilidade,
                score,
                null,
                new ArrayList<>(),
                ClassificationSource.RULE_BASED
        );
    }

    private int calculateScore(EnergyAnalysisRequest request) {
        int score = 0;

        if (request.consumoKwh() != null && request.consumoKwh() > 400) {
            score += 50;
        }

        if (Boolean.TRUE.equals(request.usoHorarioPico())) {
            score += 15;
        }

        if (request.quantidadeEquipamentos() != null && request.quantidadeEquipamentos() > 8) {
            score += 10;
        }

        if (request.horasAltoConsumo() != null && request.horasAltoConsumo() > 6) {
            score += 15;
        }

        if (request.tipoImovel() == PropertyType.CASA) {
            score += 5;
        } else if (request.tipoImovel() == PropertyType.COMERCIO) {
            score += 10;
        }

        return score;
    }

    private EnergyCategory determineCategory(int score) {
        if (score <= 30) return EnergyCategory.EFICIENTE;
        if (score <= 60) return EnergyCategory.MODERADO;
        return EnergyCategory.INEFICIENTE;
    }
}
