package br.com.g9.energiai.backend.dto.response;

import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;

import java.math.BigDecimal;
import java.util.List;

public record EnergyAnalysisResponse(
        EnergyCategory categoria,
        Double probabilidade,
        Integer score,
        BigDecimal custoEstimadoMensal,
        List<String> recomendacoes,
        ClassificationSource fonteClassificacao
) {
}
