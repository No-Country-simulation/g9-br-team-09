package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedEnergyRecommendationServiceTest {

    private static final String PEAK_HOURS_RECOMMENDATION =
        "Reduzir o uso de equipamentos durante horários de pico.";
    private static final String HIGH_CONSUMPTION_RECOMMENDATION =
        "Avaliar equipamentos com alto consumo energético.";
    private static final String HIGH_USAGE_HOURS_RECOMMENDATION =
        "Distribuir o consumo ao longo do dia.";
    private static final String MANY_DEVICES_RECOMMENDATION =
        "Verificar a eficiência energética dos equipamentos.";
    private static final String EFFICIENT_HABITS_RECOMMENDATION =
        "Manter os hábitos atuais e acompanhar o consumo mensalmente.";
    private static final String DEFAULT_RECOMMENDATION =
        "Acompanhar o consumo mensalmente para identificar oportunidades de economia.";

    private final RuleBasedEnergyRecommendationService service = new RuleBasedEnergyRecommendationService();

    @Test
    @DisplayName("Deve evitar recomendação contraditória quando o classificador real retornar EFICIENTE com correção de pico")
    void shouldAvoidContradictoryEfficientRecommendationWhenClassifierStillFlagsPeakUsage() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(100.0, true, 2, PropertyType.CASA, 2);
        RuleBasedEnergyClassifier classifier = new RuleBasedEnergyClassifier();

        EnergyAnalysisResponse classification = classifier.classify(request);
        List<String> recommendations = service.generate(request, classification.categoria());

        assertEquals(EnergyCategory.EFICIENTE, classification.categoria());
        assertNotNull(recommendations);
        assertFalse(recommendations.isEmpty());
        assertTrue(recommendations.contains(PEAK_HOURS_RECOMMENDATION));
        assertFalse(recommendations.contains(EFFICIENT_HABITS_RECOMMENDATION));
    }

    @Test
    @DisplayName("Deve recomendar avaliar equipamentos quando consumoKwh for maior que 400")
    void shouldRecommendApplianceEvaluationWhenConsumptionIsHigh() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(500.0, false, 2, PropertyType.CASA, 2);
        List<String> recommendations = service.generate(request, EnergyCategory.INEFICIENTE);

        assertTrue(recommendations.contains(HIGH_CONSUMPTION_RECOMMENDATION));
    }

    @Test
    @DisplayName("Não deve recomendar avaliar equipamentos quando consumoKwh for exatamente 400")
    void shouldNotRecommendApplianceEvaluationAtExactConsumptionThreshold() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(400.0, false, 2, PropertyType.APARTAMENTO, 2);
        List<String> recommendations = service.generate(request, EnergyCategory.MODERADO);

        assertNotNull(recommendations);
        assertFalse(recommendations.isEmpty());
        assertFalse(recommendations.contains(HIGH_CONSUMPTION_RECOMMENDATION));
    }

    @Test
    @DisplayName("Deve recomendar distribuir consumo quando horasAltoConsumo for maior que 6")
    void shouldRecommendDistributionWhenUsageHoursAreHigh() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(100.0, false, 2, PropertyType.CASA, 10);
        List<String> recommendations = service.generate(request, EnergyCategory.MODERADO);

        assertTrue(recommendations.contains(HIGH_USAGE_HOURS_RECOMMENDATION));
    }

    @Test
    @DisplayName("Não deve recomendar distribuir consumo quando horasAltoConsumo for exatamente 6")
    void shouldNotRecommendDistributionAtExactUsageHoursThreshold() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(100.0, false, 2, PropertyType.APARTAMENTO, 6);
        List<String> recommendations = service.generate(request, EnergyCategory.MODERADO);

        assertNotNull(recommendations);
        assertFalse(recommendations.isEmpty());
        assertFalse(recommendations.contains(HIGH_USAGE_HOURS_RECOMMENDATION));
    }

    @Test
    @DisplayName("Deve recomendar verificar eficiência quando quantidadeEquipamentos for maior que 8")
    void shouldRecommendEfficiencyCheckWhenDeviceCountIsHigh() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(100.0, false, 15, PropertyType.CASA, 2);
        List<String> recommendations = service.generate(request, EnergyCategory.MODERADO);

        assertTrue(recommendations.contains(MANY_DEVICES_RECOMMENDATION));
    }

    @Test
    @DisplayName("Não deve recomendar verificar eficiência quando quantidadeEquipamentos for exatamente 8")
    void shouldNotRecommendEfficiencyCheckAtExactDeviceThreshold() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(100.0, false, 8, PropertyType.APARTAMENTO, 2);
        List<String> recommendations = service.generate(request, EnergyCategory.MODERADO);

        assertNotNull(recommendations);
        assertFalse(recommendations.isEmpty());
        assertFalse(recommendations.contains(MANY_DEVICES_RECOMMENDATION));
    }

    @Test
    @DisplayName("Deve recomendar manutenção de hábitos apenas para cenário realmente eficiente")
    void shouldRecommendHabitMaintenanceOnlyWhenNoCorrectiveActionExists() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(100.0, false, 2, PropertyType.APARTAMENTO, 2);
        List<String> recommendations = service.generate(request, EnergyCategory.EFICIENTE);

        assertEquals(1, recommendations.size());
        assertTrue(recommendations.contains(EFFICIENT_HABITS_RECOMMENDATION));
        assertFalse(recommendations.contains(PEAK_HOURS_RECOMMENDATION));
        assertFalse(recommendations.contains(HIGH_CONSUMPTION_RECOMMENDATION));
        assertFalse(recommendations.contains(HIGH_USAGE_HOURS_RECOMMENDATION));
        assertFalse(recommendations.contains(MANY_DEVICES_RECOMMENDATION));
    }

    @Test
    @DisplayName("Deve retornar recomendação genérica quando nenhum critério for atingido")
    void shouldReturnDefaultRecommendationWhenNoCriteriaMet() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(100.0, false, 2, PropertyType.APARTAMENTO, 2);
        List<String> recommendations = service.generate(request, EnergyCategory.MODERADO);

        assertNotNull(recommendations);
        assertFalse(recommendations.isEmpty());
        assertEquals(DEFAULT_RECOMMENDATION, recommendations.getFirst());
    }

    @Test
    @DisplayName("Deve acumular múltiplas recomendações conforme os dados de entrada")
    void shouldAccumulateMultipleRecommendations() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(500.0, true, 2, PropertyType.CASA, 2);
        List<String> recommendations = service.generate(request, EnergyCategory.INEFICIENTE);

        assertEquals(2, recommendations.size());
        assertTrue(recommendations.contains(PEAK_HOURS_RECOMMENDATION));
        assertTrue(recommendations.contains(HIGH_CONSUMPTION_RECOMMENDATION));
    }
}
