package br.com.g9.energiai.backend.client.ml;

import br.com.g9.energiai.backend.client.ml.dto.MlPredictionRequest;
import br.com.g9.energiai.backend.client.ml.dto.MlPredictionResponse;
import br.com.g9.energiai.backend.client.ml.exception.MlPredictionClientException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RestMlPredictionClient implements MlPredictionClient {

    private final RestClient restClient;

    public RestMlPredictionClient(@Qualifier("mlRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public MlPredictionResponse predict(MlPredictionRequest request) {
        try {
            MlPredictionResponse response = restClient.post()
                    .uri("/predict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MlPredictionResponse.class);

            if (response == null) {
                throw new MlPredictionClientException("A API de ML retornou uma resposta sem corpo");
            }

            return response;
        } catch (RestClientException exception) {
            throw new MlPredictionClientException("Falha ao chamar a API de ML", exception);
        }
    }
}
