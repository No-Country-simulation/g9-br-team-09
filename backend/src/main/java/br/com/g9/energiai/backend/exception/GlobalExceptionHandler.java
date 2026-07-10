package br.com.g9.energiai.backend.exception;


import br.com.g9.energiai.backend.dto.response.ApiErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.InvalidFormatException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        return new ApiErrorResponse(LocalDateTime.now(), 400, "VALIDATION_ERROR", Objects.requireNonNull(e.getFieldError()).getDefaultMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        var cause = e.getCause();

        if (cause instanceof InvalidFormatException ife && ife.getTargetType().isEnum()) {
            var enumsValues = ife.getTargetType().getEnumConstants();
            var message = String.format("Valor inválido. Aceitos: %s", Arrays.toString(enumsValues));
            return new ApiErrorResponse(LocalDateTime.now(), 400, "ENUM_TYPE_ERROR", message);
        }

        if (cause instanceof InvalidFormatException ife) {
            var field = ife.getPath().isEmpty() ? "" : ife.getPath().getFirst().getPropertyName();
            var message = String.format("Campo %s possui tipo inválido", field);
            return new ApiErrorResponse(LocalDateTime.now(), 400, "INVALID_TYPE_ERROR", message);
        }
        return new ApiErrorResponse(LocalDateTime.now(), 400, "HTTP_MESSAGE_ERROR", "Corpo JSON mal estruturado. Verifique a sintaxe");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleResourceNotFound(ResourceNotFoundException e) {
        return new ApiErrorResponse(LocalDateTime.now(), 404, "NOT_FOUND_ERROR", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorResponse handleException(Exception e) {
        log.error("Erro inesperado: {}", e.getMessage(), e);
        return new ApiErrorResponse(LocalDateTime.now(), 500, "INTERNAL_ERROR", "Erro interno no servidor");
    }
}


