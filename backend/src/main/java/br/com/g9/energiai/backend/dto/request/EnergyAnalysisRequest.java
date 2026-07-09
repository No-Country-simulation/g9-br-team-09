package br.com.g9.energiai.backend.dto.request;

import br.com.g9.energiai.backend.enums.PropertyType;

public record EnergyAnalysisRequest (
    Double energyConsumptionKwh,
    Boolean peakHourUsage,
    Integer equipmentCount,
    PropertyType propertyType,
    Integer highConsumptionHours
){}
