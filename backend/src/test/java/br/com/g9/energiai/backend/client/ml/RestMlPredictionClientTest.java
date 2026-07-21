package br.com.g9.energiai.backend.client.ml;

import br.com.g9.energiai.backend.client.ml.dto.MlPredictionRequest;
import br.com.g9.energiai.backend.client.ml.dto.MlPredictionResponse;
import br.com.g9.energiai.backend.client.ml.exception.MlPredictionClientException;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadGateway;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestMlPredictionClientTest {

    private static final String BASE_URL = "http://ml-api.test";

    private MockRestServiceServer server;
    private RestMlPredictionClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestMlPredictionClient(builder.build());
    }

    @Test
    void shouldSendExpectedPredictionRequestAndDeserializeResponse() {
        server.expect(requestTo(BASE_URL + "/predict"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "consumo_kwh":420.0,
                          "uso_horario_pico":true,
                          "quantidade_equipamentos":10,
                          "tipo_imovel":"CASA",
                          "horas_alto_consumo":8
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "categoria":"INEFICIENTE",
                          "probabilidade":0.81,
                          "score":81,
                          "recomendacoes":["Reduzir consumo"],
                          "modelo_versao":"energy-classifier-v1"
                        }
                        """, MediaType.APPLICATION_JSON));

        MlPredictionResponse response = client.predict(
                new MlPredictionRequest(420.0, true, 10, PropertyType.CASA, 8)
        );

        assertEquals(EnergyCategory.INEFICIENTE, response.categoria());
        assertEquals(0.81, response.probabilidade());
        assertEquals(81, response.score());
        assertEquals(List.of("Reduzir consumo"), response.recomendacoes());
        assertEquals("energy-classifier-v1", response.modeloVersao());
        server.verify();
    }

    @Test
    void shouldPropagateHttpErrorsAsTechnicalFailure() {
        server.expect(requestTo(BASE_URL + "/predict"))
                .andRespond(withBadGateway());

        MlPredictionClientException exception = assertThrows(
                MlPredictionClientException.class,
                () -> client.predict(new MlPredictionRequest(420.0, true, 10, PropertyType.CASA, 8))
        );

        assertInstanceOf(RestClientResponseException.class, exception.getCause());
    }

    @Test
    void shouldPropagateMalformedJsonAsTechnicalFailure() {
        server.expect(requestTo(BASE_URL + "/predict"))
                .andRespond(withSuccess("{invalid-json", MediaType.APPLICATION_JSON));

        MlPredictionClientException exception = assertThrows(
                MlPredictionClientException.class,
                () -> client.predict(new MlPredictionRequest(420.0, true, 10, PropertyType.CASA, 8))
        );

        assertEquals("Falha ao chamar a API de ML", exception.getMessage());
        assertInstanceOf(RestClientException.class, exception.getCause());
    }

    @Test
    void shouldRejectSuccessfulResponseWithoutBody() {
        server.expect(requestTo(BASE_URL + "/predict"))
                .andRespond(withSuccess());

        MlPredictionClientException exception = assertThrows(
                MlPredictionClientException.class,
                () -> client.predict(new MlPredictionRequest(420.0, true, 10, PropertyType.CASA, 8))
        );

        assertEquals("A API de ML retornou uma resposta sem corpo", exception.getMessage());
    }

    @Test
    void shouldPropagateConnectionFailuresAsTechnicalFailure() {
        server.expect(requestTo(BASE_URL + "/predict"))
                .andRespond(withException(new IOException("Connection refused")));

        MlPredictionClientException exception = assertThrows(
                MlPredictionClientException.class,
                () -> client.predict(new MlPredictionRequest(420.0, true, 10, PropertyType.CASA, 8))
        );

        assertInstanceOf(IOException.class, exception.getCause().getCause());
    }

    @Test
    void shouldPropagateTimeoutAsTechnicalFailure() {
        server.expect(requestTo(BASE_URL + "/predict"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        MlPredictionClientException exception = assertThrows(
                MlPredictionClientException.class,
                () -> client.predict(new MlPredictionRequest(420.0, true, 10, PropertyType.CASA, 8))
        );

        assertInstanceOf(SocketTimeoutException.class, exception.getCause().getCause());
    }
}
