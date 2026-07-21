package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.client.ml.MlPredictionClient;
import br.com.g9.energiai.backend.client.ml.dto.MlPredictionRequest;
import br.com.g9.energiai.backend.client.ml.dto.MlPredictionResponse;
import br.com.g9.energiai.backend.client.ml.exception.MlPredictionClientException;
import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnergyAnalysisOrchestratorTest {

    private final EnergyAnalysisRequest request = new EnergyAnalysisRequest(
            420.0, true, 10, PropertyType.CASA, 8
    );

    @Mock
    private MlPredictionClient mlPredictionClient;

    @Mock
    private EnergyClassifier energyClassifier;

    @Mock
    private EnergyRecommendationService energyRecommendationService;

    private EnergyAnalysisOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new EnergyAnalysisOrchestrator(
                mlPredictionClient, energyClassifier, energyRecommendationService
        );
    }

    @Test
    void shouldUseMlPredictionWithoutCallingLocalServices() {
        List<String> mlRecommendations = List.of("Recomendação do modelo");
        when(mlPredictionClient.predict(any(MlPredictionRequest.class))).thenReturn(
                new MlPredictionResponse(EnergyCategory.MODERADO, 0.81, 81, mlRecommendations, "v1")
        );

        EnergyAnalysisResult result = orchestrator.analyze(request);

        ArgumentCaptor<MlPredictionRequest> requestCaptor = ArgumentCaptor.forClass(MlPredictionRequest.class);
        verify(mlPredictionClient).predict(requestCaptor.capture());
        assertEquals(new MlPredictionRequest(420.0, true, 10, PropertyType.CASA, 8), requestCaptor.getValue());
        assertEquals(EnergyCategory.MODERADO, result.categoria());
        assertEquals(0.81, result.probabilidade());
        assertEquals(81, result.score());
        assertEquals(mlRecommendations, result.recomendacoes());
        assertEquals(ClassificationSource.ML_MODEL, result.fonteClassificacao());
        verify(energyClassifier, never()).classify(any());
        verify(energyRecommendationService, never()).generate(any(), any());
    }

    @ParameterizedTest(name = "deve usar fallback para {0}")
    @MethodSource("technicalFailures")
    void shouldUseFallbackWhenMlClientFails(String ignoredDescription, MlPredictionClientException failure) {
        when(mlPredictionClient.predict(any(MlPredictionRequest.class))).thenThrow(failure);

        EnergyAnalysisResult result = analyzeWithConfiguredFallback();

        assertFallbackResult(result);
    }

    @ParameterizedTest(name = "deve usar fallback para resposta inválida: {0}")
    @MethodSource("invalidPredictions")
    void shouldUseFallbackWhenMlPredictionIsInvalid(String ignoredDescription, MlPredictionResponse prediction) {
        when(mlPredictionClient.predict(any(MlPredictionRequest.class))).thenReturn(prediction);

        EnergyAnalysisResult result = analyzeWithConfiguredFallback();

        assertFallbackResult(result);
    }

    @ParameterizedTest(name = "deve aceitar limites válidos: probabilidade={0}, score={1}")
    @MethodSource("validBoundaries")
    void shouldAcceptMlPredictionBoundaryValues(double probability, int score) {
        when(mlPredictionClient.predict(any(MlPredictionRequest.class))).thenReturn(
                new MlPredictionResponse(EnergyCategory.EFICIENTE, probability, score, List.of("Dica"), null)
        );

        EnergyAnalysisResult result = orchestrator.analyze(request);

        assertEquals(probability, result.probabilidade());
        assertEquals(score, result.score());
        assertEquals(ClassificationSource.ML_MODEL, result.fonteClassificacao());
        verify(energyClassifier, never()).classify(any());
        verify(energyRecommendationService, never()).generate(any(), any());
    }

    private EnergyAnalysisResult analyzeWithConfiguredFallback() {
        when(energyClassifier.classify(request)).thenReturn(new EnergyAnalysisResponse(
                null, EnergyCategory.INEFICIENTE, 0.95, 95, null, List.of(), ClassificationSource.RULE_BASED
        ));
        when(energyRecommendationService.generate(request, EnergyCategory.INEFICIENTE))
                .thenReturn(List.of("Recomendação local"));

        return orchestrator.analyze(request);
    }

    private void assertFallbackResult(EnergyAnalysisResult result) {
        assertEquals(EnergyCategory.INEFICIENTE, result.categoria());
        assertEquals(0.95, result.probabilidade());
        assertEquals(95, result.score());
        assertEquals(List.of("Recomendação local"), result.recomendacoes());
        assertEquals(ClassificationSource.RULE_BASED_FALLBACK, result.fonteClassificacao());
        verify(energyClassifier).classify(request);
        verify(energyRecommendationService).generate(request, EnergyCategory.INEFICIENTE);
    }

    private static Stream<Arguments> technicalFailures() {
        return Stream.of(
                Arguments.of("conexão recusada", new MlPredictionClientException("Falha", new IOException("Connection refused"))),
                Arguments.of("timeout", new MlPredictionClientException("Falha", new SocketTimeoutException("Read timed out"))),
                Arguments.of("erro HTTP", new MlPredictionClientException("Falha", new RestClientException("Bad gateway"))),
                Arguments.of("falha de desserialização", new MlPredictionClientException("Falha", new RestClientException("Invalid JSON")))
        );
    }

    private static Stream<Arguments> invalidPredictions() {
        return Stream.of(
                Arguments.of("response nula", null),
                Arguments.of("categoria nula", new MlPredictionResponse(null, 0.5, 50, List.of("Dica"), null)),
                Arguments.of("probabilidade nula", new MlPredictionResponse(EnergyCategory.EFICIENTE, null, 50, List.of("Dica"), null)),
                Arguments.of("probabilidade abaixo de zero", new MlPredictionResponse(EnergyCategory.EFICIENTE, -0.01, 50, List.of("Dica"), null)),
                Arguments.of("probabilidade acima de um", new MlPredictionResponse(EnergyCategory.EFICIENTE, 1.01, 50, List.of("Dica"), null)),
                Arguments.of("probabilidade NaN", new MlPredictionResponse(EnergyCategory.EFICIENTE, Double.NaN, 50, List.of("Dica"), null)),
                Arguments.of("probabilidade infinita", new MlPredictionResponse(EnergyCategory.EFICIENTE, Double.POSITIVE_INFINITY, 50, List.of("Dica"), null)),
                Arguments.of("score nulo", new MlPredictionResponse(EnergyCategory.EFICIENTE, 0.5, null, List.of("Dica"), null)),
                Arguments.of("score abaixo de zero", new MlPredictionResponse(EnergyCategory.EFICIENTE, 0.5, -1, List.of("Dica"), null)),
                Arguments.of("score acima de cem", new MlPredictionResponse(EnergyCategory.EFICIENTE, 0.5, 101, List.of("Dica"), null)),
                Arguments.of("recomendações nulas", new MlPredictionResponse(EnergyCategory.EFICIENTE, 0.5, 50, null, null)),
                Arguments.of("recomendações vazias", new MlPredictionResponse(EnergyCategory.EFICIENTE, 0.5, 50, List.of(), null))
        );
    }

    private static Stream<Arguments> validBoundaries() {
        return Stream.of(
                Arguments.of(0.0, 0),
                Arguments.of(1.0, 100)
        );
    }
}
