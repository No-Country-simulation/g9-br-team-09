package br.com.g9.energiai.backend.dto.response;

import br.com.g9.energiai.backend.enums.EnergyCategory;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EnergyAnalysisSummaryResponse(
        Long id,
        EnergyCategory categoria,
        Double probabilidade,
        Integer score,

        @JsonProperty("custo_estimado_mensal")
        @Schema(description = "Custo mensal estimado da energia.")
        BigDecimal custoEstimadoMensal,

        @JsonProperty("criado_em")
        @Schema(description = "Data e hora da realização da análise.")
        LocalDateTime criadoEm
) {}
