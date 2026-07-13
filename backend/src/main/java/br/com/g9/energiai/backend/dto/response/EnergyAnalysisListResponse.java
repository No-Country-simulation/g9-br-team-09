package br.com.g9.energiai.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record EnergyAnalysisListResponse(
    List<EnergyAnalysisSummaryResponse> analises,

    @JsonProperty("pagina_atual")
    Integer paginaAtual,

    @JsonProperty("tamanho_pagina")
    Integer tamanhoPagina,

    @JsonProperty("total_elementos")
    Long totalElementos,

    @JsonProperty("total_paginas")
    Integer totalPaginas
) {
}
