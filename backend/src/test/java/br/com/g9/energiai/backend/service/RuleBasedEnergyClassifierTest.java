package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedEnergyClassifierTest {

    private final RuleBasedEnergyClassifier classifier = new RuleBasedEnergyClassifier();

    @Test
    @DisplayName("Deve classificar como EFICIENTE (Score <= 30)")
    void shouldClassifyAsEfficient() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(150.0, false, 3, PropertyType.CASA, 2);
        EnergyAnalysisResponse response = classifier.classify(request);

        assertEquals(EnergyCategory.EFICIENTE, response.categoria());
        assertEquals(5, response.score());
    }

    @Test
    @DisplayName("Deve classificar como MODERADO (31 <= Score <= 60)")
    void shouldClassifyAsModerate() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(200.0, true, 10, PropertyType.CASA, 8);
        EnergyAnalysisResponse response = classifier.classify(request);

        assertEquals(EnergyCategory.MODERADO, response.categoria());
        assertEquals(45, response.score());
    }

    @Test
    @DisplayName("Deve classificar como INEFICIENTE (Score > 60)")
    void shouldClassifyAsInefficient() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(500.0, true, 10, PropertyType.COMERCIO, 8);
        EnergyAnalysisResponse response = classifier.classify(request);

        assertEquals(EnergyCategory.INEFICIENTE, response.categoria());
        assertTrue(response.score() >= 61);
    }

    @Test
    @DisplayName("Deve classificar score 30 como EFICIENTE")
    void shouldClassifyBoundaryScore30AsEfficient() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(200.0, true, 3, PropertyType.APARTAMENTO, 8);
        EnergyAnalysisResponse response = classifier.classify(request);

        assertEquals(30, response.score());
        assertEquals(EnergyCategory.EFICIENTE, response.categoria());
    }

    @Test
    @DisplayName("Deve classificar score 31 como MODERADO")
    void shouldClassifyBoundaryScore31AsModerate() {
        assertEquals(EnergyCategory.MODERADO, classifier.determineCategory(31));
    }

    @Test
    @DisplayName("Deve classificar score 60 como MODERADO")
    void shouldClassifyBoundaryScore60AsModerate() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(500.0, false, 9, PropertyType.APARTAMENTO, 2);
        EnergyAnalysisResponse response = classifier.classify(request);

        assertEquals(60, response.score());
        assertEquals(EnergyCategory.MODERADO, response.categoria());
    }

    @Test
    @DisplayName("Deve classificar score 61 como INEFICIENTE")
    void shouldClassifyBoundaryScore61AsInefficient() {
        assertEquals(EnergyCategory.INEFICIENTE, classifier.determineCategory(61));
    }

    @Test
    @DisplayName("Deve calcular probabilidade estimada a partir do score")
    void shouldReturnEstimatedProbability() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(500.0, false, 9, PropertyType.APARTAMENTO, 2);
        EnergyAnalysisResponse response = classifier.classify(request);

        assertEquals(0.60, response.probabilidade());
        assertEquals(ClassificationSource.RULE_BASED, response.fonteClassificacao());
    }

    @Test
    @DisplayName("Deve tratar apartamento sem pontuacao adicional por tipo de imovel")
    void shouldNotAddPropertyTypeScoreForApartment() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(200.0, false, 3, PropertyType.APARTAMENTO, 2);
        EnergyAnalysisResponse response = classifier.classify(request);

        assertEquals(0, response.score());
        assertEquals(EnergyCategory.EFICIENTE, response.categoria());
    }

    @Test
    @DisplayName("Deve tratar campos nulos como ausencia de pontos")
    void shouldHandleNullFieldsWithoutAddingPoints() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(null, null, null, null, null);
        EnergyAnalysisResponse response = classifier.classify(request);

        assertEquals(0, response.score());
        assertEquals(0.0, response.probabilidade());
        assertEquals(EnergyCategory.EFICIENTE, response.categoria());
        assertIterableEquals(java.util.List.of(), response.recomendacoes());
        assertEquals(ClassificationSource.RULE_BASED, response.fonteClassificacao());
    }

    @Test
    @DisplayName("Deve retornar categoria e probabilidade coerentes para score calculado")
    void shouldCalculateScoreAndProbabilityConsistently() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(500.0, false, 9, PropertyType.APARTAMENTO, 2);

        int score = classifier.calculateScore(request);

        assertEquals(60, score);
        assertEquals(EnergyCategory.MODERADO, classifier.determineCategory(score));
        assertEquals(0.60, classifier.estimateProbability(score));
    }
}
