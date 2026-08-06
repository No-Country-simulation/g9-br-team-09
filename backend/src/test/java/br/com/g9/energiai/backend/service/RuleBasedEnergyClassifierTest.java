package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedEnergyClassifierTest {

    private final RuleBasedEnergyClassifier classifier = new RuleBasedEnergyClassifier();

    @ParameterizedTest(name = "score {0} deve ser classificado como {1}")
    @MethodSource("categoryBoundaries")
    void shouldClassifyCategoryAtScoreBoundaries(int score, EnergyCategory expectedCategory) {
        assertEquals(expectedCategory, classifier.determineCategory(score));
    }

    @Test
    @DisplayName("Deve manter a confiança heurística quando o score atingir o valor máximo")
    void shouldUseHeuristicConfidenceWhenScoreIsMaximum() {
        EnergyAnalysisRequest extremeRequest = new EnergyAnalysisRequest(1000.0, true, 50, PropertyType.COMERCIO, 24);
        EnergyAnalysisResponse response = classifier.classify(extremeRequest);
        assertEquals(100, response.score());
        assertEquals(EnergyCategory.INEFICIENTE, response.categoria());
        assertEquals(0.75, response.probabilidade());
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
    @DisplayName("Deve aplicar pontuação adicional para imóvel CASA")
    void shouldApplyAdditionalScoreForHouse() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(100.0, false, 2, PropertyType.CASA, 2);

        assertEquals(5, classifier.calculateScore(request));
    }

    @Test
    @DisplayName("Deve aplicar pontuação adicional para imóvel COMERCIO")
    void shouldApplyAdditionalScoreForCommercialProperty() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(100.0, false, 2, PropertyType.COMERCIO, 2);

        assertEquals(10, classifier.calculateScore(request));
    }

    @Test
    @DisplayName("Não deve adicionar pontuação para imóvel sem regra adicional")
    void shouldNotApplyAdditionalScoreForPropertyWithoutRule() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(100.0, false, 2, PropertyType.APARTAMENTO, 2);

        assertEquals(0, classifier.calculateScore(request));
    }

    @Test
    @DisplayName("Deve retornar confiança heurística para score cinco")
    void shouldReturnHeuristicConfidenceForScoreFive() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(100.0, false, 2, PropertyType.CASA, 2);
        EnergyAnalysisResponse response = classifier.classify(request);

        assertEquals(5, response.score());
        assertEquals(EnergyCategory.EFICIENTE, response.categoria());
        assertEquals(0.75, response.probabilidade());
    }

    @Test
    @DisplayName("Deve lidar com campos nulos atribuindo score zero e confiança heurística")
    void shouldHandleNullFieldsAsZeroScore() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(null, null, null, null, null);
        EnergyAnalysisResponse response = classifier.classify(request);
        assertEquals(0, response.score());
        assertEquals(0.75, response.probabilidade());
        assertEquals(EnergyCategory.EFICIENTE, response.categoria());
        assertEquals(ClassificationSource.RULE_BASED, response.fonteClassificacao());
    }

    @ParameterizedTest(name = "score calculado {1} deve manter confiança heurística")
    @MethodSource("ruleBasedRequests")
    void shouldReturnHeuristicConfidenceForAllRuleBasedClassifications(
            EnergyAnalysisRequest request, int expectedScore) {
        EnergyAnalysisResponse response = classifier.classify(request);

        assertEquals(expectedScore, response.score());
        assertEquals(0.75, response.probabilidade());
        assertTrue(response.probabilidade() >= 0.0 && response.probabilidade() <= 1.0);
    }

    private static Stream<Arguments> categoryBoundaries() {
        return Stream.of(
                Arguments.of(0, EnergyCategory.EFICIENTE),
                Arguments.of(30, EnergyCategory.EFICIENTE),
                Arguments.of(31, EnergyCategory.MODERADO),
                Arguments.of(60, EnergyCategory.MODERADO),
                Arguments.of(61, EnergyCategory.INEFICIENTE),
                Arguments.of(100, EnergyCategory.INEFICIENTE)
        );
    }

    private static Stream<Arguments> ruleBasedRequests() {
        return Stream.of(
                Arguments.of(new EnergyAnalysisRequest(100.0, false, 2, PropertyType.APARTAMENTO, 2), 0),
                Arguments.of(new EnergyAnalysisRequest(200.0, true, 3, PropertyType.APARTAMENTO, 8), 30),
                Arguments.of(new EnergyAnalysisRequest(500.0, false, 9, PropertyType.APARTAMENTO, 2), 60),
                Arguments.of(new EnergyAnalysisRequest(1000.0, true, 50, PropertyType.COMERCIO, 24), 100)
        );
    }
}
