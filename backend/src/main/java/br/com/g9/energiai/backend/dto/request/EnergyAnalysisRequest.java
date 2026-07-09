package br.com.g9.energiai.backend.dto.request;

import br.com.g9.energiai.backend.enums.PropertyType;

public record EnergyAnalysisRequest(
    Double consumoKwh,
    Boolean usoHorarioPico,
    Integer quantidadeEquipamentos,
    PropertyType tipoImovel,
    Integer horasAltoConsumo
) {
}
