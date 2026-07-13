package br.com.g9.energiai.backend.mapper;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisSummaryResponse;
import br.com.g9.energiai.backend.entity.EnergyAnalysisEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class EnergyAnalysisMapper {

    public EnergyAnalysisEntity toEntity(EnergyAnalysisRequest request,
                                         EnergyAnalysisResponse classification,
                                         BigDecimal estimatedCost,
                                         List<String> recommendations) {
        List<String> safeRecommendations = recommendations == null ? List.of() : List.copyOf(recommendations);

        return EnergyAnalysisEntity.builder()
                .consumoKwh(request.consumoKwh())
                .usoHorarioPico(request.usoHorarioPico())
                .quantidadeEquipamentos(request.quantidadeEquipamentos())
                .tipoImovel(request.tipoImovel())
                .horasAltoConsumo(request.horasAltoConsumo())
                .categoria(classification.categoria())
                .probabilidade(classification.probabilidade())
                .score(classification.score())
                .custoEstimadoMensal(estimatedCost)
                .recomendacoes(safeRecommendations)
                .fonteClassificacao(classification.fonteClassificacao())
                .build();
    }

    public EnergyAnalysisResponse toResponse(EnergyAnalysisEntity entity) {
        List<String> safeRecommendations =
                entity.getRecomendacoes() == null ? List.of() : List.copyOf(entity.getRecomendacoes());

        return new EnergyAnalysisResponse(
                entity.getId(),
                entity.getCategoria(),
                entity.getProbabilidade(),
                entity.getScore(),
                entity.getCustoEstimadoMensal(),
                safeRecommendations,
                entity.getFonteClassificacao()
        );
    }

    public EnergyAnalysisSummaryResponse toSummaryResponse(EnergyAnalysisEntity entity) {
        return new EnergyAnalysisSummaryResponse(
                entity.getId(),
                entity.getCategoria(),
                entity.getProbabilidade(),
                entity.getScore(),
                entity.getCustoEstimadoMensal(),
                entity.getCreatedAt()
        );
    }
}
