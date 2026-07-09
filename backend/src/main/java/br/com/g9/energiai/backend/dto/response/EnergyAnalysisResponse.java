package br.com.g9.energiai.backend.dto.response;

import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EnergyAnalysisResponse (
        EnergyCategory energyCategory,
        Double probability,
        BigDecimal estimatedCost,
        LocalDateTime analysisDate,
        ClassificationSource classificationSource
){}
