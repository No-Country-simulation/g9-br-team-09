package br.com.g9.energiai.backend.exception;

import br.com.g9.energiai.backend.dto.response.ApiErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    private static final String ENUM_TYPE_ERROR = "ENUM_TYPE_ERROR";
    private static final String INVALID_TYPE_ERROR = "INVALID_TYPE_ERROR";
    private static final String HTTP_MESSAGE_ERROR = "HTTP_MESSAGE_ERROR";
    private static final String BAD_REQUEST_ERROR = "BAD_REQUEST_ERROR";
    private static final String NOT_FOUND_ERROR = "NOT_FOUND_ERROR";
    private static final String METHOD_NOT_ALLOWED_ERROR = "METHOD_NOT_ALLOWED_ERROR";
    private static final String UNSUPPORTED_MEDIA_TYPE_ERROR = "UNSUPPORTED_MEDIA_TYPE_ERROR";
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private static final String GENERIC_VALIDATION_MESSAGE = "Dados de entrada inválidos";
    private static final String GENERIC_INVALID_BODY_TYPE_MESSAGE = "Um campo do corpo da requisição possui tipo inválido";
    private static final String GENERIC_HTTP_MESSAGE = "Corpo da requisição inválido";
    private static final String GENERIC_INTERNAL_MESSAGE = "Erro interno no servidor";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                 HttpHeaders headers,
                                                                 HttpStatusCode status,
                                                                 WebRequest request) {
        return buildResponse(status, VALIDATION_ERROR, buildValidationMessage(exception), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        InvalidFormatException invalidFormatException = findInvalidFormatException(exception);

        if (invalidFormatException == null) {
            return buildResponse(status, HTTP_MESSAGE_ERROR, GENERIC_HTTP_MESSAGE, headers, request);
        }

        if (invalidFormatException.getTargetType() != null && invalidFormatException.getTargetType().isEnum()) {
            return buildResponse(
                status,
                ENUM_TYPE_ERROR,
                buildEnumMessage(invalidFormatException.getTargetType()),
                headers,
                request
            );
        }

        return buildResponse(
            status,
            INVALID_TYPE_ERROR,
            buildInvalidTypeMessage(invalidFormatException),
            headers,
            request
        );
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException exception,
                                                                          HttpHeaders headers,
                                                                          HttpStatusCode status,
                                                                          WebRequest request) {
        String message = "Parâmetro obrigatório ausente: " + exception.getParameterName();
        return buildResponse(status, BAD_REQUEST_ERROR, message, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception,
                                                        HttpHeaders headers,
                                                        HttpStatusCode status,
                                                        WebRequest request) {
        if (exception instanceof MethodArgumentTypeMismatchException mismatchException) {
            String message = "Parâmetro " + mismatchException.getName() + " possui valor inválido";
            return buildResponse(status, BAD_REQUEST_ERROR, message, headers, request);
        }

        return buildResponse(status, BAD_REQUEST_ERROR, "Parâmetro da requisição possui valor inválido", headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(NoHandlerFoundException exception,
                                                                   HttpHeaders headers,
                                                                   HttpStatusCode status,
                                                                   WebRequest request) {
        return buildResponse(status, NOT_FOUND_ERROR, "Rota não encontrada", headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException exception,
                                                                    HttpHeaders headers,
                                                                    HttpStatusCode status,
                                                                    WebRequest request) {
        return buildResponse(status, NOT_FOUND_ERROR, "Rota não encontrada", headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException exception,
                                                                         HttpHeaders headers,
                                                                         HttpStatusCode status,
                                                                         WebRequest request) {
        return buildResponse(status, METHOD_NOT_ALLOWED_ERROR, "Método HTTP não suportado", headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception,
                                                                     HttpHeaders headers,
                                                                     HttpStatusCode status,
                                                                     WebRequest request) {
        return buildResponse(status, UNSUPPORTED_MEDIA_TYPE_ERROR, "Media type não suportado", headers, request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(buildBody(HttpStatus.NOT_FOUND, NOT_FOUND_ERROR, exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("Erro inesperado no processamento da requisição", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(buildBody(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR, GENERIC_INTERNAL_MESSAGE));
    }

    private ResponseEntity<Object> buildResponse(HttpStatusCode status,
                                                 String errorCode,
                                                 String message,
                                                 HttpHeaders headers,
                                                 WebRequest request) {
        return createResponseEntity(buildBody(status, errorCode, message), headers, status, request);
    }

    private ApiErrorResponse buildBody(HttpStatusCode status, String errorCode, String message) {
        return new ApiErrorResponse(LocalDateTime.now(), status.value(), errorCode, message);
    }

    private String buildValidationMessage(MethodArgumentNotValidException exception) {
        List<String> fieldMessages = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .filter(error -> hasText(error.getField()) && hasText(error.getDefaultMessage()))
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .sorted()
            .toList();

        if (!fieldMessages.isEmpty()) {
            return String.join("; ", fieldMessages);
        }

        return exception.getBindingResult()
            .getGlobalErrors()
            .stream()
            .map(error -> error.getDefaultMessage())
            .filter(this::hasText)
            .findFirst()
            .orElse(GENERIC_VALIDATION_MESSAGE);
    }

    private InvalidFormatException findInvalidFormatException(HttpMessageNotReadableException exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof InvalidFormatException invalidFormatException) {
                return invalidFormatException;
            }
            current = current.getCause();
        }

        return null;
    }

    private String buildEnumMessage(Class<?> targetType) {
        Object[] acceptedValues = targetType.getEnumConstants();
        return "Valor inválido. Aceitos: " + Arrays.toString(acceptedValues);
    }

    private String buildInvalidTypeMessage(InvalidFormatException exception) {
        String fieldPath = exception.getPath()
            .stream()
            .map(this::formatPathReference)
            .filter(this::hasText)
            .reduce(this::appendPathSegment)
            .orElse("");

        if (!hasText(fieldPath)) {
            return GENERIC_INVALID_BODY_TYPE_MESSAGE;
        }

        return "Campo " + fieldPath + " possui tipo inválido";
    }

    private String formatPathReference(JacksonException.Reference reference) {
        if (hasText(reference.getPropertyName())) {
            return reference.getPropertyName();
        }

        if (reference.getIndex() >= 0) {
            return "[" + reference.getIndex() + "]";
        }

        return "";
    }

    private String appendPathSegment(String currentPath, String nextSegment) {
        if (currentPath.isEmpty()) {
            return nextSegment;
        }
        if (nextSegment.startsWith("[")) {
            return currentPath + nextSegment;
        }
        return currentPath + "." + nextSegment;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
