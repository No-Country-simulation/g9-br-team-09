package br.com.g9.energiai.backend.dto.response;

import java.util.List;

public record EnergyAnalysisListResponse(
    List<EnergyAnalysisSummaryResponse> analises
) {
}
