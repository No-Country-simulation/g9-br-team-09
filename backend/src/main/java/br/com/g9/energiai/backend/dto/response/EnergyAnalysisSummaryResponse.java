package br.com.g9.energiai.backend.dto.response;

import br.com.g9.energiai.backend.enums.EnergyCategory;

public record EnergyAnalysisSummaryResponse (
        EnergyCategory energyCategory,
        Double probability
){
}
