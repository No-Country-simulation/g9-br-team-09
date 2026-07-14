package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedEnergyClassifierTest {

    private final RuleBasedEnergyClassifier classifier = new RuleBasedEnergyClassifier();

    @Test
    @DisplayName("Deve classificar como EFICIENTE quando o score for igual a 30")
    void shouldClassifyAsEfficientWhenScoreIsExactly30() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(200.0, true, 3, PropertyType.APARTAMENTO, 8);
        EnergyAnalysisResponse response = classifier.classify(request);
        assertEquals(30, response.score());
        assertEquals(EnergyCategory.EFICIENTE, response.categoria());
    }

    @Test
    @DisplayName("Deve classificar como MODERADO quando o score for igual a 31")
    void shouldClassifyAsModerateWhenScoreIsExactly31() {
        assertEquals(EnergyCategory.MODERADO, classifier.determineCategory(31));
    }

    @Test
    @DisplayName("Deve classificar como MODERADO quando o score for igual a 60")
    void shouldClassifyAsModerateWhenScoreIsExactly60() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(500.0, false, 9, PropertyType.APARTAMENTO, 2);
        EnergyAnalysisResponse response = classifier.classify(request);
        assertEquals(60, response.score());
        assertEquals(EnergyCategory.MODERADO, response.categoria());
    }

    @Test
    @DisplayName("Deve classificar como INEFICIENTE quando o score for igual a 61")
    void shouldClassifyAsInefficientWhenScoreIsExactly61() {
        assertEquals(EnergyCategory.INEFICIENTE, classifier.determineCategory(61));
    }

    @Test
    @DisplayName("Deve limitar a probabilidade em 1.00 quando o score atingir o valor máximo")
    void shouldCapProbabilityAtOneWhenScoreIsMaximum() {
        EnergyAnalysisRequest extremeRequest = new EnergyAnalysisRequest(1000.0, true, 50, PropertyType.COMERCIO, 24);
        EnergyAnalysisResponse response = classifier.classify(extremeRequest);
        assertEquals(100, response.score());
        assertEquals(1.0, response.probabilidade());
    }

    @Test
    @DisplayName("Deve calcular corretamente a pontuação para cada regra de negócio")
    void shouldCalculateScoreForEachBusinessRule() {
        EnergyAnalysisRequest houseRequest = new EnergyAnalysisRequest(401.0, true, 9, PropertyType.CASA, 7);
        assertEquals(95, classifier.calculateScore(houseRequest));
        EnergyAnalysisRequest commercialRequest = new EnergyAnalysisRequest(399.0, false, 2, PropertyType.COMERCIO, 1);
        assertEquals(10, classifier.calculateScore(commercialRequest));
    }

    @Test
    @DisplayName("Deve lidar com campos nulos atribuindo pontuação zero")
    void shouldHandleNullFieldsAsZeroScore() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(null, null, null, null, null);
        EnergyAnalysisResponse response = classifier.classify(request);
        assertEquals(0, response.score());
        assertEquals(0.0, response.probabilidade());
        assertEquals(EnergyCategory.EFICIENTE, response.categoria());
        assertEquals(ClassificationSource.RULE_BASED, response.fonteClassificacao());
    }
}
