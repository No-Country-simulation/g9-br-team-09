package br.com.g9.energiai.backend.dto.request;

import br.com.g9.energiai.backend.enums.PropertyType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(name = "EnergyAnalysisRequest", description = "Dados de entrada para analise energetica")
public record EnergyAnalysisRequest(
        @JsonProperty("consumo_kwh")
        @Schema(description = "Consumo mensal de energia eletrica em kWh.", example = "420")
        @NotNull(message = "O consumo não deve ser nulo")
        @Positive(message = "O consumo deve ser um valor positivo")
        Double consumoKwh,

        @JsonProperty("uso_horario_pico")
        @Schema(
            description = "Indica se ha utilizacao relevante de equipamentos no horario de pico.",
            example = "true"
        )
        @NotNull(message = "O uso em horário de pico não deve ser nulo")
        Boolean usoHorarioPico,

        @JsonProperty("quantidade_equipamentos")
        @Schema(
            description = "Quantidade de equipamentos eletricos considerados na analise.",
            example = "10"
        )
        @NotNull(message = "A quantidade de equipamentos não deve ser nula")
        @Min(value = 1, message = "Deve haver pelo menos 1 equipamento registrado")
        Integer quantidadeEquipamentos,

        @JsonProperty("tipo_imovel")
        @Schema(
            description = "Tipo do imovel analisado.",
            example = "CASA",
            allowableValues = {"CASA", "APARTAMENTO", "COMERCIO", "ESCRITORIO", "INDUSTRIA", "OUTRO"}
        )
        @NotNull(message = "O tipo de imóvel não deve ser nulo")
        PropertyType tipoImovel,

        @JsonProperty("horas_alto_consumo")
        @Schema(
            description = "Quantidade media diaria de horas de alto consumo.",
            example = "8"
        )
        @NotNull(message = "As horas de alto consumo não devem ser nulas")
        @Min(value = 0, message = "Valor mínimo permitido 0")
        @Max(value = 24, message = "Valor máximo permitido 24")
        Integer horasAltoConsumo
) {
}
