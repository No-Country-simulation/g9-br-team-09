package br.com.g9.energiai.backend.client.ml.dto;

import br.com.g9.energiai.backend.enums.EnergyCategory;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record MlPredictionResponse(
        EnergyCategory categoria,
        Double probabilidade,
        Integer score,
        List<String> recomendacoes,
        @JsonProperty("modelo_versao") String modeloVersao
) {
}
