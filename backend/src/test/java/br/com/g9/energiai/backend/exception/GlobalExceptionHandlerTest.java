package br.com.g9.energiai.backend.exception;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.ApiErrorResponse;
import jakarta.validation.Valid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import tools.jackson.databind.exc.InvalidFormatException;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final HttpHeaders headers = new HttpHeaders();
    private final WebRequest request = new ServletWebRequest(new MockHttpServletRequest());

    @Test
    @DisplayName("Retorna mensagem determinística para múltiplos erros de validação")
    void shouldConcatenateSortedValidationErrors() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "energyAnalysisRequest");
        bindingResult.addError(new FieldError("energyAnalysisRequest", "quantidadeEquipamentos",
            "Deve haver pelo menos 1 equipamento registrado"));
        bindingResult.addError(new FieldError("energyAnalysisRequest", "consumoKwh",
            "O consumo deve ser um valor positivo"));
        MethodArgumentNotValidException exception = buildValidationException(bindingResult);

        ApiErrorResponse response = extractResponse(handler.handleMethodArgumentNotValid(
            exception, headers, HttpStatus.BAD_REQUEST, request
        ));

        assertEquals(400, response.status());
        assertEquals("VALIDATION_ERROR", response.error());
        assertEquals(
            "consumo_kwh: O consumo deve ser um valor positivo; quantidade_equipamentos: Deve haver pelo menos 1 equipamento registrado",
            response.message()
        );
        assertNotNull(response.timestamp());
    }

    @Test
    @DisplayName("Usa erro global quando não houver FieldError")
    void shouldUseGlobalErrorMessageWhenFieldErrorsAreMissing() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "energyAnalysisRequest");
        bindingResult.addError(new ObjectError("energyAnalysisRequest", "Dados inconsistentes"));
        MethodArgumentNotValidException exception = buildValidationException(bindingResult);

        ApiErrorResponse response = extractResponse(handler.handleMethodArgumentNotValid(
            exception, headers, HttpStatus.BAD_REQUEST, request
        ));

        assertEquals(400, response.status());
        assertEquals("VALIDATION_ERROR", response.error());
        assertEquals("Dados inconsistentes", response.message());
    }

    @Test
    @DisplayName("Usa mensagem genérica quando não houver mensagens válidas de validação")
    void shouldUseGenericValidationMessageWhenBindingResultHasNoMessages() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "energyAnalysisRequest");
        bindingResult.addError(new ObjectError("energyAnalysisRequest", ""));
        MethodArgumentNotValidException exception = buildValidationException(bindingResult);

        ApiErrorResponse response = extractResponse(handler.handleMethodArgumentNotValid(
            exception, headers, HttpStatus.BAD_REQUEST, request
        ));

        assertEquals("Dados de entrada inválidos", response.message());
    }

    @Test
    @DisplayName("Retorna mensagem genérica para tipo inválido sem path identificável")
    void shouldReturnGenericMessageWhenJacksonPathIsEmpty() {
        InvalidFormatException invalidFormatException = InvalidFormatException.from(
            null, "", "abc", Integer.class
        );
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
            "Unreadable JSON",
            invalidFormatException,
            new MockHttpInputMessage("{}".getBytes(StandardCharsets.UTF_8))
        );

        ApiErrorResponse response = extractResponse(handler.handleHttpMessageNotReadable(
            exception, headers, HttpStatus.BAD_REQUEST, request
        ));

        assertEquals(400, response.status());
        assertEquals("INVALID_TYPE_ERROR", response.error());
        assertEquals("Um campo do corpo da requisição possui tipo inválido", response.message());
    }

    @Test
    @DisplayName("Retorna 404 e preserva mensagem pública para ResourceNotFoundException")
    void shouldReturnNotFoundResponseForResourceNotFoundException() {
        ApiErrorResponse response = handler.handleResourceNotFound(new ResourceNotFoundException("Análise não encontrada"))
            .getBody();

        assertNotNull(response);
        assertEquals(404, response.status());
        assertEquals("NOT_FOUND_ERROR", response.error());
        assertEquals("Análise não encontrada", response.message());
    }

    private ApiErrorResponse extractResponse(org.springframework.http.ResponseEntity<Object> responseEntity) {
        assertNotNull(responseEntity);
        Object body = responseEntity.getBody();
        assertInstanceOf(ApiErrorResponse.class, body);
        return (ApiErrorResponse) body;
    }

    private MethodArgumentNotValidException buildValidationException(BeanPropertyBindingResult bindingResult) {
        try {
            Method method = ValidationFixture.class.getDeclaredMethod("handle", EnergyAnalysisRequest.class);
            MethodParameter parameter = new MethodParameter(method, 0);
            return new MethodArgumentNotValidException(parameter, bindingResult);
        }
        catch (NoSuchMethodException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static class ValidationFixture {
        @SuppressWarnings("unused")
        void handle(@Valid EnergyAnalysisRequest request) {
        }
    }
}
