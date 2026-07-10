package br.com.g9.energiai.backend.exception;

import br.com.g9.energiai.backend.dto.response.ApiErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalHandlerExceptionTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Nested
    @DisplayName("handleMethodArgumentNotValid")
    class HandleMethodArgumentNotValid {

        @Test
        @DisplayName("Deve retornar 400 e código VALIDATION_ERROR com a mensagem do campo inválido")
        void shouldReturnValidationErrorWithFieldMessage() {
            FieldError fieldError = new FieldError(
                    "energyAnalysisRequest", "consumoKwh", "O consumo deve ser um valor positivo");

            MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
            when(ex.getFieldError()).thenReturn(fieldError);

            ApiErrorResponse response = handler.handleMethodArgumentNotValid(ex);

            assertNotNull(response);
            assertEquals(400, response.status());
            assertEquals("VALIDATION_ERROR", response.error());
            assertEquals("O consumo deve ser um valor positivo", response.message());
            assertNotNull(response.timestamp());
        }

        @Test
        @DisplayName("Deve lançar NullPointerException quando não houver FieldError")
        void shouldThrowNullPointerExceptionWhenFieldErrorIsNull() {
            MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
            when(ex.getFieldError()).thenReturn(null);

            assertThrows(NullPointerException.class, () -> handler.handleMethodArgumentNotValid(ex));
        }
    }

    @Nested
    @DisplayName("handleHttpMessageNotReadable")
    class HandleHttpMessageNotReadable {

        @Test
        @DisplayName("Deve retornar 400 e ENUM_TYPE_ERROR quando a causa for enum inválido")
        void deveRetornarEnumTypeErrorParaEnumInvalido() {
            InvalidFormatException ife = mock(InvalidFormatException.class);
            enum PropertyType {CASA, APARTAMENTO, COMERCIO, ESCRITORIO, INDUSTRIA, OUTRO}
            when(ife.getTargetType()).thenReturn((Class) PropertyType.class);

            HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
            when(ex.getCause()).thenReturn(ife);

            ApiErrorResponse response = handler.handleHttpMessageNotReadable(ex);

            assertEquals(400, response.status());
            assertEquals("ENUM_TYPE_ERROR", response.error());
            assertTrue(response.message().contains("Valor inválido"));
            assertTrue(response.message().contains("CASA"));
            assertTrue(response.message().contains("APARTAMENTO"));
            assertTrue(response.message().contains("COMERCIO"));
            assertTrue(response.message().contains("ESCRITORIO"));
            assertTrue(response.message().contains("INDUSTRIA"));
            assertTrue(response.message().contains("OUTRO"));
        }

        @Test
        @DisplayName("Deve retornar 400 e INVALID_TYPE_ERROR quando a causa for tipo inválido em campo específico")
        void shouldReturnInvalidTypeErrorWithFieldName() {
            InvalidFormatException ife = mock(InvalidFormatException.class);
            JacksonException.Reference reference = mock(JacksonException.Reference.class);
            enum PropertyType {CASA, APARTAMENTO, COMERCIO, ESCRITORIO, INDUSTRIA, OUTRO}

            //Pode ser qualquer classe. Objetivo: não cair no caso ENUM_TYPE_ERROR
            when(ife.getTargetType()).thenReturn((Class) String.class);

            when(ife.getPath()).thenReturn(List.of(reference));
            when(reference.getPropertyName()).thenReturn("quantidadeEquipamentos");

            HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
            when(ex.getCause()).thenReturn(ife);

            ApiErrorResponse response = handler.handleHttpMessageNotReadable(ex);

            assertEquals(400, response.status());
            assertEquals("INVALID_TYPE_ERROR", response.error());
            assertEquals("Campo quantidadeEquipamentos possui tipo inválido", response.message());
        }

        @Test
        @DisplayName("Deve retornar 400 e INVALID_TYPE_ERROR com campo vazio quando o path estiver vazio")
        void shouldReturnInvalidTypeErrorWithEmptyField() {
            InvalidFormatException ife = mock(InvalidFormatException.class);

            when(ife.getTargetType()).thenReturn((Class) String.class);
            when(ife.getPath()).thenReturn(List.of());

            HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
            when(ex.getCause()).thenReturn(ife);

            ApiErrorResponse response = handler.handleHttpMessageNotReadable(ex);

            assertEquals(400, response.status());
            assertEquals("INVALID_TYPE_ERROR", response.error());
            assertEquals("Campo  possui tipo inválido", response.message());
        }

        @Test
        @DisplayName("Deve retornar 400 e HTTP_MESSAGE_ERROR quando a causa não for InvalidFormatException")
        void shouldReturnHttpMessageErrorToJsonMalformed() {
            HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
            when(ex.getCause()).thenReturn(new RuntimeException("JSON malformado"));

            ApiErrorResponse response = handler.handleHttpMessageNotReadable(ex);

            assertEquals(400, response.status());
            assertEquals("HTTP_MESSAGE_ERROR", response.error());
            assertEquals("Corpo JSON mal estruturado. Verifique a sintaxe", response.message());
        }

        @Test
        @DisplayName("Deve retornar HTTP_MESSAGE_ERROR quando não houver causa (cause == null)")
        void shouldReturnHttpMessageErrorWhenCauseIsNull() {
            HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
            when(ex.getCause()).thenReturn(null);

            ApiErrorResponse response = handler.handleHttpMessageNotReadable(ex);

            assertEquals(400, response.status());
            assertEquals("HTTP_MESSAGE_ERROR", response.error());
        }
    }

    @Nested
    @DisplayName("handleResourceNotFound")
    class HandleResourceNotFound {

        @Test
        @DisplayName("Deve retornar 404 e NOT_FOUND_ERROR com a mensagem da exceção")
        void shouldReturnNotFoundErrorWithMessage() {
            ResourceNotFoundException ex = new ResourceNotFoundException("Recurso não encontrada");

            ApiErrorResponse response = handler.handleResourceNotFound(ex);

            assertEquals(404, response.status());
            assertEquals("NOT_FOUND_ERROR", response.error());
            assertEquals("Recurso não encontrada", response.message());
            assertNotNull(response.timestamp());
        }
    }

    @Nested
    @DisplayName("handleException")
    class HandleException {

        @Test
        @DisplayName("Deve retornar 500 e INTERNAL_ERROR sem expor a mensagem original da exceção")
        void shouldReturnGenericInternalError() {
            Exception ex = new RuntimeException("NullPointerException na linha 42 do service");

            ApiErrorResponse response = handler.handleException(ex);

            assertEquals(500, response.status());
            assertEquals("INTERNAL_ERROR", response.error());
            assertEquals("Erro interno no servidor", response.message());
        }
    }
}