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

    @Override
    public List<String> generate(EnergyAnalysisRequest request, EnergyCategory categoria) {
        List<String> recomendacoes = new ArrayList<>();

        if (Boolean.TRUE.equals(request.usoHorarioPico())) {
            recomendacoes.add("Reduzir o uso de equipamentos durante horários de pico.");
        }

        if (request.consumoKwh() != null && request.consumoKwh() > HIGH_CONSUMPTION_THRESHOLD) {
            recomendacoes.add("Avaliar equipamentos com alto consumo energético.");
        }

        if (request.horasAltoConsumo() != null && request.horasAltoConsumo() > HIGH_USAGE_HOURS_THRESHOLD) {
            recomendacoes.add("Distribuir o consumo ao longo do dia.");
        }

        if (request.quantidadeEquipamentos() != null && request.quantidadeEquipamentos() > MANY_DEVICES_THRESHOLD) {
            recomendacoes.add("Verificar a eficiência energética dos equipamentos.");
        }

        if (EnergyCategory.EFICIENTE.equals(categoria)) {
            recomendacoes.add("Manter os hábitos atuais e acompanhar o consumo mensalmente.");
        }

        if (recomendacoes.isEmpty()) {
            recomendacoes.add("Acompanhar o consumo mensalmente para identificar oportunidades de economia.");
        }

        return recomendacoes;
    }
}
