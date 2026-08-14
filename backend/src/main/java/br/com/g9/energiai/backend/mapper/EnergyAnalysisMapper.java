package br.com.g9.energiai.backend.mapper;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisDetailResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisSummaryResponse;
import br.com.g9.energiai.backend.entity.AppUser;
import br.com.g9.energiai.backend.entity.EnergyAnalysisEntity;
import br.com.g9.energiai.backend.service.EnergyAnalysisResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class EnergyAnalysisMapper {

    private static final int PROBABILITY_SCALE = 17;

    public EnergyAnalysisEntity toEntity(EnergyAnalysisRequest request,
                                         EnergyAnalysisResult analysisResult,
                                         BigDecimal estimatedCost,
                                         AppUser user) {
        List<String> safeRecommendations = analysisResult.recomendacoes() == null
                ? List.of()
                : List.copyOf(analysisResult.recomendacoes());

        return EnergyAnalysisEntity.builder()
                .consumoKwh(request.consumoKwh())
                .usoHorarioPico(request.usoHorarioPico())
                .quantidadeEquipamentos(request.quantidadeEquipamentos())
                .tipoImovel(request.tipoImovel())
                .horasAltoConsumo(request.horasAltoConsumo())
                .categoria(analysisResult.categoria())
                .probabilidade(normalizeProbability(analysisResult.probabilidade()))
                .score(analysisResult.score())
                .custoEstimadoMensal(estimatedCost)
                .recomendacoes(safeRecommendations)
                .fonteClassificacao(analysisResult.fonteClassificacao())
                .user(user)
                .build();
    }

    public EnergyAnalysisEntity toEntity(EnergyAnalysisRequest request,
                                         EnergyAnalysisResponse classification,
                                         BigDecimal estimatedCost,
                                         List<String> recommendations,
                                         AppUser user) {
        List<String> safeRecommendations = recommendations == null ? List.of() : List.copyOf(recommendations);

        return EnergyAnalysisEntity.builder()
                .consumoKwh(request.consumoKwh())
                .usoHorarioPico(request.usoHorarioPico())
                .quantidadeEquipamentos(request.quantidadeEquipamentos())
                .tipoImovel(request.tipoImovel())
                .horasAltoConsumo(request.horasAltoConsumo())
                .categoria(classification.categoria())
                .probabilidade(normalizeProbability(classification.probabilidade()))
                .score(classification.score())
                .custoEstimadoMensal(estimatedCost)
                .recomendacoes(safeRecommendations)
                .fonteClassificacao(classification.fonteClassificacao())
                .user(user)
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

    public EnergyAnalysisDetailResponse toDetailResponse(EnergyAnalysisEntity entity) {
        List<String> safeRecommendations =
                entity.getRecomendacoes() == null ? List.of() : List.copyOf(entity.getRecomendacoes());

        return new EnergyAnalysisDetailResponse(
                entity.getId(),
                entity.getConsumoKwh(),
                entity.getUsoHorarioPico(),
                entity.getQuantidadeEquipamentos(),
                entity.getTipoImovel(),
                entity.getHorasAltoConsumo(),
                entity.getCategoria(),
                entity.getProbabilidade(),
                entity.getScore(),
                entity.getCustoEstimadoMensal(),
                safeRecommendations,
                entity.getFonteClassificacao(),
                entity.getCreatedAt()
        );
    }

    private Double normalizeProbability(Double probability) {
        if (probability == null) {
            return null;
        }

        return BigDecimal.valueOf(probability)
                .setScale(PROBABILITY_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
