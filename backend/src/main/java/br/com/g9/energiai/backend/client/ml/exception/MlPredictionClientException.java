package br.com.g9.energiai.backend.client.ml.exception;

public class MlPredictionClientException extends RuntimeException {

    public MlPredictionClientException(String message) {
        super(message);
    }

    public MlPredictionClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
