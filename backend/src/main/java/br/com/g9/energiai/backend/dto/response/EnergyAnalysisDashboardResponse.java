package br.com.g9.energiai.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "EnergyAnalysisDashboardResponse", description = "Resumo estatístico das análises para o dashboard")
public record EnergyAnalysisDashboardResponse(
        @JsonProperty("total_analises")
        @Schema(description = "Quantidade total de análises realizadas.", example = "35")
        Long totalAnalises,

        @JsonProperty("media_consumo_kwh")
        @Schema(description = "Média de consumo mensal em kWh.", example = "382.5")
        Double mediaConsumoKwh,

        @JsonProperty("media_custo_mensal")
        @Schema(description = "Média de custo mensal em reais.", example = "286.87")
        BigDecimal mediaCustoMensal,

        @JsonProperty("total_eficiente")
        @Schema(description = "Total de análises classificadas como EFICIENTE.", example = "8")
        Long totalEficiente,

        @JsonProperty("total_moderado")
        @Schema(description = "Total de análises classificadas como MODERADO.", example = "16")
        Long totalModerado,

        @JsonProperty("total_ineficiente")
        @Schema(description = "Total de análises classificadas como INEFICIENTE.", example = "11")
        Long totalIneficiente
) {}
