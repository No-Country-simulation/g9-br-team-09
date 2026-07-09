package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
