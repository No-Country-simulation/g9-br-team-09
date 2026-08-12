package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;

import java.util.List;

public record EnergyAnalysisResult(
        EnergyCategory categoria,
        Double probabilidade,
        Integer score,
        List<String> recomendacoes,
        ClassificationSource fonteClassificacao
) {
    public EnergyAnalysisResult {
        recomendacoes = List.copyOf(recomendacoes);
    }
}
