package br.com.g9.energiai.backend.integration;

import br.com.g9.energiai.backend.client.ml.RestMlPredictionClient;
import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import br.com.g9.energiai.backend.service.EnergyAnalysisOrchestrator;
import br.com.g9.energiai.backend.service.EnergyAnalysisResult;
import br.com.g9.energiai.backend.service.EnergyClassifier;
import br.com.g9.energiai.backend.service.EnergyRecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BackendFastApiIntegrationTest {

    private static final String BASE_URL = "http://ml-api.test";

    private final EnergyAnalysisRequest request = new EnergyAnalysisRequest(
            420.0, true, 10, PropertyType.CASA, 8
    );

    private MockRestServiceServer server;
    private EnergyClassifier energyClassifier;
    private EnergyRecommendationService energyRecommendationService;
    private EnergyAnalysisOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();

        energyClassifier = mock(EnergyClassifier.class);
        energyRecommendationService = mock(EnergyRecommendationService.class);
        orchestrator = new EnergyAnalysisOrchestrator(
                new RestMlPredictionClient(builder.build()),
                energyClassifier,
                energyRecommendationService
        );
    }

    @Test
    void shouldUseMlModelForValidFastApiResponse() {
        server.expect(requestTo(BASE_URL + "/predict"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "consumo_kwh": 420.0,
                          "uso_horario_pico": true,
                          "quantidade_equipamentos": 10,
                          "tipo_imovel": "CASA",
                          "horas_alto_consumo": 8
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "categoria": "INEFICIENTE",
                          "probabilidade": 0.81,
                          "score": 81,
                          "recomendacoes": ["Reduzir consumo"],
                          "modelo_versao": "energy-classifier-v2"
                        }
                        """, MediaType.APPLICATION_JSON));

        EnergyAnalysisResult result = orchestrator.analyze(request);

        assertEquals(EnergyCategory.INEFICIENTE, result.categoria());
        assertEquals(0.81, result.probabilidade());
        assertEquals(81, result.score());
        assertEquals(List.of("Reduzir consumo"), result.recomendacoes());
        assertEquals(ClassificationSource.ML_MODEL, result.fonteClassificacao());
        verify(energyClassifier, never()).classify(any());
        verify(energyRecommendationService, never()).generate(any(), any());
        server.verify();
    }

    @Test
    void shouldUseFallbackWhenFastApiIsUnavailable() {
        EnergyAnalysisResult result = analyzeWithFallback(
                withException(new IOException("Connection refused"))
        );

        assertFallbackResult(result);
    }

    @Test
    void shouldUseFallbackWhenFastApiTimesOut() {
        EnergyAnalysisResult result = analyzeWithFallback(
                withException(new SocketTimeoutException("Read timed out"))
        );

        assertFallbackResult(result);
    }

    @Test
    void shouldUseFallbackWhenFastApiReturnsSemanticallyInvalidPayload() {
        EnergyAnalysisResult result = analyzeWithFallback(withSuccess("""
                {
                  "categoria": "INEFICIENTE",
                  "probabilidade": 1.01,
                  "score": 81,
                  "recomendacoes": ["Reduzir consumo"],
                  "modelo_versao": "energy-classifier-v2"
                }
                """, MediaType.APPLICATION_JSON));

        assertFallbackResult(result);
    }

    private EnergyAnalysisResult analyzeWithFallback(ResponseCreator response) {
        server.expect(requestTo(BASE_URL + "/predict"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(response);
        configureFallback();

        EnergyAnalysisResult result = orchestrator.analyze(request);

        server.verify();
        return result;
    }

    private void configureFallback() {
        when(energyClassifier.classify(request)).thenReturn(new EnergyAnalysisResponse(
                null, EnergyCategory.MODERADO, 0.65, 65, null, List.of(), ClassificationSource.RULE_BASED
        ));
        when(energyRecommendationService.generate(request, EnergyCategory.MODERADO))
                .thenReturn(List.of("Recomendação local"));
    }

    private void assertFallbackResult(EnergyAnalysisResult result) {
        assertEquals(EnergyCategory.MODERADO, result.categoria());
        assertEquals(0.65, result.probabilidade());
        assertEquals(65, result.score());
        assertEquals(List.of("Recomendação local"), result.recomendacoes());
        assertEquals(ClassificationSource.RULE_BASED_FALLBACK, result.fonteClassificacao());
        verify(energyClassifier).classify(request);
        verify(energyRecommendationService).generate(request, EnergyCategory.MODERADO);
    }
}
