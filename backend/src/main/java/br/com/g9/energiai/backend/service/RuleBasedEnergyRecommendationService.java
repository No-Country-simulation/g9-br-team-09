package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RuleBasedEnergyRecommendationService implements EnergyRecommendationService {

    private static final int HIGH_CONSUMPTION_THRESHOLD = 400;
    private static final int MANY_DEVICES_THRESHOLD = 8;
    private static final int HIGH_USAGE_HOURS_THRESHOLD = 6;
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

    @Override
    public List<String> generate(EnergyAnalysisRequest request, EnergyCategory categoria) {
        List<String> recomendacoes = new ArrayList<>();

        if (Boolean.TRUE.equals(request.usoHorarioPico())) {
            recomendacoes.add(PEAK_HOURS_RECOMMENDATION);
        }

        if (request.consumoKwh() != null && request.consumoKwh() > HIGH_CONSUMPTION_THRESHOLD) {
            recomendacoes.add(HIGH_CONSUMPTION_RECOMMENDATION);
        }

        if (request.horasAltoConsumo() != null && request.horasAltoConsumo() > HIGH_USAGE_HOURS_THRESHOLD) {
            recomendacoes.add(HIGH_USAGE_HOURS_RECOMMENDATION);
        }

        if (request.quantidadeEquipamentos() != null && request.quantidadeEquipamentos() > MANY_DEVICES_THRESHOLD) {
            recomendacoes.add(MANY_DEVICES_RECOMMENDATION);
        }

        if (EnergyCategory.EFICIENTE.equals(categoria) && recomendacoes.isEmpty()) {
            recomendacoes.add(EFFICIENT_HABITS_RECOMMENDATION);
        }

        if (recomendacoes.isEmpty()) {
            recomendacoes.add(DEFAULT_RECOMMENDATION);
        }

        return recomendacoes;
    }
}
