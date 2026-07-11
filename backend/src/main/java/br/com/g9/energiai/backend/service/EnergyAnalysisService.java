package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.entity.EnergyAnalysisEntity;
import br.com.g9.energiai.backend.repository.EnergyAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EnergyAnalysisService {

    private final EnergyClassifier energyClassifier;
    private final EnergyCostCalculator energyCostCalculator;
    private final EnergyRecommendationService energyRecommendationService;
    private final EnergyAnalysisRepository energyAnalysisRepository;

    @Transactional
    public EnergyAnalysisResponse analyze(EnergyAnalysisRequest request) {

        EnergyAnalysisResponse classification = energyClassifier.classify(request);

        BigDecimal estimatedCost = energyCostCalculator.calculate(request.consumoKwh());

        List<String> recommendations = energyRecommendationService.generate(
                request,
                classification.categoria()
        );

        EnergyAnalysisEntity entity = EnergyAnalysisEntity.builder()
                .consumoKwh(request.consumoKwh())
                .usoHorarioPico(request.usoHorarioPico())
                .quantidadeEquipamentos(request.quantidadeEquipamentos())
                .tipoImovel(request.tipoImovel())
                .horasAltoConsumo(request.horasAltoConsumo())
                .categoria(classification.categoria())
                .probabilidade(classification.probabilidade())
                .score(classification.score())
                .custoEstimadoMensal(estimatedCost)
                .fonteClassificacao(classification.fonteClassificacao())
                .recomendacoes(recommendations)
                .build();

        EnergyAnalysisEntity savedEntity = energyAnalysisRepository.save(entity);

        return new EnergyAnalysisResponse(
                savedEntity.getId(),
                savedEntity.getCategoria(),
                savedEntity.getProbabilidade(),
                savedEntity.getScore(),
                savedEntity.getCustoEstimadoMensal(),
                savedEntity.getRecomendacoes(),
                savedEntity.getFonteClassificacao()
        );
    }
}
