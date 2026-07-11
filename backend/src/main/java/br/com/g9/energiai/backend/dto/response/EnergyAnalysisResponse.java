package br.com.g9.energiai.backend.dto.response;

import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

public record EnergyAnalysisResponse(
        Long id,
        EnergyCategory categoria,
        Double probabilidade,
        Integer score,
        @JsonProperty("custo_estimado_mensal")
        BigDecimal custoEstimadoMensal,
        List<String> recomendacoes,
        @JsonProperty("fonte_classificacao")
        ClassificationSource fonteClassificacao
) {}
