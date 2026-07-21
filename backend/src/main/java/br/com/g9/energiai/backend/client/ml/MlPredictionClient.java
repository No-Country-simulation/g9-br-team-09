package br.com.g9.energiai.backend.client.ml;

import br.com.g9.energiai.backend.client.ml.dto.MlPredictionRequest;
import br.com.g9.energiai.backend.client.ml.dto.MlPredictionResponse;

public interface MlPredictionClient {

    MlPredictionResponse predict(MlPredictionRequest request);
}
