package br.com.g9.energiai.backend.client.ml.dto;

import br.com.g9.energiai.backend.enums.PropertyType;
import com.fasterxml.jackson.annotation.JsonProperty;

public record MlPredictionRequest(
        @JsonProperty("consumo_kwh") Double consumoKwh,
        @JsonProperty("uso_horario_pico") Boolean usoHorarioPico,
        @JsonProperty("quantidade_equipamentos") Integer quantidadeEquipamentos,
        @JsonProperty("tipo_imovel") PropertyType tipoImovel,
        @JsonProperty("horas_alto_consumo") Integer horasAltoConsumo
) {
}
