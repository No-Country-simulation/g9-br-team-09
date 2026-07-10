package br.com.g9.energiai.backend.dto.request;

import br.com.g9.energiai.backend.enums.PropertyType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EnergyAnalysisRequest(
        @NotNull(message = "ConsumoKwh não deve ser nulo")
        @Positive(message = "O consumo deve ser um valor positivo")
        Double consumoKwh,

        @NotNull(message = "UsoHorarioPico não deve ser nulo")
        Boolean usoHorarioPico,

        @NotNull(message = "QuantidadeEquipamentos não deve ser nulo")
        @Min(value = 1, message = "Deve haver pelo menos 1 equipamento registrado")
        Integer quantidadeEquipamentos,

        @NotNull(message = "TipoImovel não deve ser nulo")
        PropertyType tipoImovel,

        @NotNull(message = "HorasAltoConsumo não deve ser nulo")
        @Min(value = 0, message = "Valor mínimo permitido 0")
        @Max(value = 24, message = "Valor máximo permitido 24")
        Integer horasAltoConsumo
) {
}
