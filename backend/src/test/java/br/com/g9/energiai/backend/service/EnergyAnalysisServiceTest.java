package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.entity.EnergyAnalysisEntity;
import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import br.com.g9.energiai.backend.repository.EnergyAnalysisRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class EnergyAnalysisServiceTest {

    @Test
    @DisplayName("Deve orquestrar classificação, custo e recomendações preservando todos os campos da resposta")
    void shouldAssembleFinalResponseFromCollaborators() {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(
                500.0,
                true,
                10,
                PropertyType.CASA,
                8
        );
        EnergyAnalysisResponse classification = new EnergyAnalysisResponse(
                null,
                EnergyCategory.INEFICIENTE,
                0.95,
                95,
                null,
                List.of(),
                ClassificationSource.RULE_BASED
        );
        BigDecimal estimatedCost = new BigDecimal("375.00");
        List<String> recommendations = List.of(
                "Reduzir o uso de equipamentos durante horários de pico.",
                "Avaliar equipamentos com alto consumo energético."
        );

        StubEnergyClassifier energyClassifier = new StubEnergyClassifier(classification);
        StubEnergyCostCalculator energyCostCalculator = new StubEnergyCostCalculator(estimatedCost);
        CapturingEnergyRecommendationService energyRecommendationService =
                new CapturingEnergyRecommendationService(recommendations);

        StubEnergyAnalysisRepository energyAnalysisRepository = new StubEnergyAnalysisRepository();

        EnergyAnalysisService service = new EnergyAnalysisService(
                energyClassifier,
                energyCostCalculator,
                energyRecommendationService,
                energyAnalysisRepository
        );

        EnergyAnalysisResponse response = service.analyze(request);

        assertEquals(EnergyCategory.INEFICIENTE, response.categoria());
        assertEquals(0.95, response.probabilidade());
        assertEquals(95, response.score());
        assertEquals(ClassificationSource.RULE_BASED, response.fonteClassificacao());
        assertEquals(estimatedCost, response.custoEstimadoMensal());
        assertEquals(recommendations, response.recomendacoes());
        assertEquals(1L, response.id());

        assertSame(request, energyClassifier.receivedRequest);
        assertEquals(500.0, energyCostCalculator.receivedConsumption);
        assertSame(request, energyRecommendationService.receivedRequest);
        assertEquals(EnergyCategory.INEFICIENTE, energyRecommendationService.receivedCategory);
    }

    private static final class StubEnergyClassifier implements EnergyClassifier {
        private final EnergyAnalysisResponse response;
        private EnergyAnalysisRequest receivedRequest;

        private StubEnergyClassifier(EnergyAnalysisResponse response) {
            this.response = response;
        }

        @Override
        public EnergyAnalysisResponse classify(EnergyAnalysisRequest request) {
            this.receivedRequest = request;
            return response;
        }
    }

    private static final class StubEnergyCostCalculator extends EnergyCostCalculator {
        private final BigDecimal result;
        private Double receivedConsumption;

        private StubEnergyCostCalculator(BigDecimal result) {
            super(BigDecimal.ONE);
            this.result = result;
        }

        @Override
        public BigDecimal calculate(Double consumptionKwh) {
            this.receivedConsumption = consumptionKwh;
            return result;
        }
    }

    private static final class CapturingEnergyRecommendationService implements EnergyRecommendationService {
        private final List<String> result;
        private EnergyAnalysisRequest receivedRequest;
        private EnergyCategory receivedCategory;

        private CapturingEnergyRecommendationService(List<String> result) {
            this.result = result;
        }

        @Override
        public List<String> generate(EnergyAnalysisRequest request, EnergyCategory categoria) {
            this.receivedRequest = request;
            this.receivedCategory = categoria;
            return result;
        }
    }

    private static final class StubEnergyAnalysisRepository implements EnergyAnalysisRepository {
        @Override
        public <S extends EnergyAnalysisEntity> S save(S entity) {
            entity.setId(1L);
            return entity;
        }

        @Override public void flush() {}
        @Override public <S extends EnergyAnalysisEntity> S saveAndFlush(S entity) { return null; }
        @Override public <S extends EnergyAnalysisEntity> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<EnergyAnalysisEntity> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> longs) {}
        @Override public void deleteAllInBatch() {}
        @Override public EnergyAnalysisEntity getOne(Long aLong) { return null; }
        @Override public EnergyAnalysisEntity getById(Long aLong) { return null; }
        @Override public EnergyAnalysisEntity getReferenceById(Long aLong) { return null; }
        @Override public <S extends EnergyAnalysisEntity> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends EnergyAnalysisEntity> List<S> findAll(Example<S> example) { return null; }
        @Override public <S extends EnergyAnalysisEntity> List<S> findAll(Example<S> example, Sort sort) { return null; }
        @Override public <S extends EnergyAnalysisEntity> Page<S> findAll(Example<S> example, Pageable pageable) { return null; }
        @Override public <S extends EnergyAnalysisEntity> long count(Example<S> example) { return 0; }
        @Override public <S extends EnergyAnalysisEntity> boolean exists(Example<S> example) { return false; }
        @Override public <S extends EnergyAnalysisEntity, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public List<EnergyAnalysisEntity> findAll() { return null; }
        @Override public List<EnergyAnalysisEntity> findAll(Sort sort) { return null; }
        @Override public Page<EnergyAnalysisEntity> findAll(Pageable pageable) { return null; }
        @Override public List<EnergyAnalysisEntity> findAllById(Iterable<Long> longs) { return null; }
        @Override public long count() { return 0; }
        @Override public void deleteById(Long aLong) {}
        @Override public void delete(EnergyAnalysisEntity entity) {}
        @Override public void deleteAllById(Iterable<? extends Long> longs) {}
        @Override public void deleteAll(Iterable<? extends EnergyAnalysisEntity> entities) {}
        @Override public void deleteAll() {}
        @Override public <S extends EnergyAnalysisEntity> List<S> saveAll(Iterable<S> entities) { return null; }
        @Override public Optional<EnergyAnalysisEntity> findById(Long aLong) { return Optional.empty(); }
        @Override public boolean existsById(Long aLong) { return false; }
    }
}
