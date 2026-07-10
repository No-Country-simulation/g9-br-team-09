package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleBasedEnergyRecommendationServiceTest {

    private final RuleBasedEnergyRecommendationService service = new RuleBasedEnergyRecommendationService();

    @Test
    @DisplayName("Deve recomendar redução no pico quando usoHorarioPico for verdadeiro")
    void shouldRecommendPeakReductionWhenPeakUsageIsTrue() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(100.0, true, 2, PropertyType.CASA, 2);
        List<String> recomendacoes = service.generate(request, EnergyCategory.MODERADO);

        assertTrue(recomendacoes.contains("Reduzir o uso de equipamentos durante horários de pico."));
    }

    @Test
    @DisplayName("Deve recomendar avaliar equipamentos quando consumoKwh for maior que 400")
    void shouldRecommendApplianceEvaluationWhenConsumptionIsHigh() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(500.0, false, 2, PropertyType.CASA, 2);
        List<String> recomendacoes = service.generate(request, EnergyCategory.INEFICIENTE);

        assertTrue(recomendacoes.contains("Avaliar equipamentos com alto consumo energético."));
    }

    @Test
    @DisplayName("Deve recomendar distribuir consumo quando horasAltoConsumo for maior que 6")
    void shouldRecommendDistributionWhenUsageHoursAreHigh() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(100.0, false, 2, PropertyType.CASA, 10);
        List<String> recomendacoes = service.generate(request, EnergyCategory.MODERADO);

        assertTrue(recomendacoes.contains("Distribuir o consumo ao longo do dia."));
    }

    @Test
    @DisplayName("Deve recomendar verificar eficiência quando quantidadeEquipamentos for maior que 8")
    void shouldRecommendEfficiencyCheckWhenDeviceCountIsHigh() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(100.0, false, 15, PropertyType.CASA, 2);
        List<String> recomendacoes = service.generate(request, EnergyCategory.MODERADO);

        assertTrue(recomendacoes.contains("Verificar a eficiência energética dos equipamentos."));
    }

    @Test
    @DisplayName("Deve recomendar manutenção de hábitos quando a categoria for EFICIENTE")
    void shouldRecommendHabitMaintenanceWhenCategoryIsEfficient() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(100.0, false, 2, PropertyType.CASA, 2);
        List<String> recomendacoes = service.generate(request, EnergyCategory.EFICIENTE);

        assertTrue(recomendacoes.contains("Manter os hábitos atuais e acompanhar o consumo mensalmente."));
    }

    @Test
    @DisplayName("Deve retornar recomendação genérica quando nenhum critério for atingido")
    void shouldReturnDefaultRecommendationWhenNoCriteriaMet() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(100.0, false, 2, PropertyType.APARTAMENTO, 2);
        List<String> recomendacoes = service.generate(request, EnergyCategory.MODERADO);

        assertNotNull(recomendacoes);
        assertFalse(recomendacoes.isEmpty());
        assertEquals("Acompanhar o consumo mensalmente para identificar oportunidades de economia.", recomendacoes.get(0));
    }

    @Test
    @DisplayName("Deve acumular múltiplas recomendações conforme os dados de entrada")
    void shouldAccumulateMultipleRecommendations() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(500.0, true, 2, PropertyType.CASA, 2);
        List<String> recomendacoes = service.generate(request, EnergyCategory.INEFICIENTE);

        assertEquals(2, recomendacoes.size());
        assertTrue(recomendacoes.contains("Reduzir o uso de equipamentos durante horários de pico."));
        assertTrue(recomendacoes.contains("Avaliar equipamentos com alto consumo energético."));
    }
}
