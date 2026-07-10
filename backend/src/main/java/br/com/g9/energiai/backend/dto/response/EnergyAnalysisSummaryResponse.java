package br.com.g9.energiai.backend.dto.response;

import br.com.g9.energiai.backend.enums.EnergyCategory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EnergyAnalysisSummaryResponse(
    Long id,
    EnergyCategory categoria,
    Double probabilidade,
    Integer score,
    BigDecimal custoEstimadoMensal,
    LocalDateTime criadoEm
) {
}
