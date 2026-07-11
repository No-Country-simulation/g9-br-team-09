package br.com.g9.energiai.backend.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ExceptionHandlingTestController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "spring.web.resources.add-mappings=true")
class GlobalExceptionHandlerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Retorna 400 VALIDATION_ERROR para consumoKwh negativo")
    void shouldReturnValidationErrorForNegativeConsumption() throws Exception {
        mockMvc.perform(post("/test/analise-energetica")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "consumo_kwh": -10,
                      "uso_horario_pico": true,
                      "quantidade_equipamentos": 10,
                      "tipo_imovel": "CASA",
                      "horas_alto_consumo": 8
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("consumo_kwh: O consumo deve ser um valor positivo"))
            .andExpect(jsonPath("$.timestamp").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*")));
    }

    @Test
    @DisplayName("Retorna 400 para consumoKwh nulo")
    void shouldReturnValidationErrorForNullConsumption() throws Exception {
        mockMvc.perform(post("/test/analise-energetica")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "consumo_kwh": null,
                      "uso_horario_pico": true,
                      "quantidade_equipamentos": 10,
                      "tipo_imovel": "CASA",
                      "horas_alto_consumo": 8
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("consumo_kwh: O consumo não deve ser nulo"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Retorna 400 para quantidadeEquipamentos igual a zero")
    void shouldReturnValidationErrorForZeroEquipmentCount() throws Exception {
        mockMvc.perform(post("/test/analise-energetica")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "consumo_kwh": 420.0,
                      "uso_horario_pico": true,
                      "quantidade_equipamentos": 0,
                      "tipo_imovel": "CASA",
                      "horas_alto_consumo": 8
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value(
                "quantidade_equipamentos: Deve haver pelo menos 1 equipamento registrado"
            ))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Retorna 400 para horasAltoConsumo menor que zero")
    void shouldReturnValidationErrorForNegativePeakHours() throws Exception {
        mockMvc.perform(post("/test/analise-energetica")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "consumo_kwh": 420.0,
                      "uso_horario_pico": true,
                      "quantidade_equipamentos": 10,
                      "tipo_imovel": "CASA",
                      "horas_alto_consumo": -1
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("horas_alto_consumo: Valor mínimo permitido 0"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Retorna 400 para horasAltoConsumo maior que 24")
    void shouldReturnValidationErrorForPeakHoursAboveLimit() throws Exception {
        mockMvc.perform(post("/test/analise-energetica")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "consumo_kwh": 420.0,
                      "uso_horario_pico": true,
                      "quantidade_equipamentos": 10,
                      "tipo_imovel": "CASA",
                      "horas_alto_consumo": 25
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("horas_alto_consumo: Valor máximo permitido 24"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Concatena múltiplos erros de validação em ordem determinística")
    void shouldReturnSortedValidationErrorsForMultipleInvalidFields() throws Exception {
        String invalidPayload = """
            {
              "consumo_kwh": -10,
              "uso_horario_pico": true,
              "quantidade_equipamentos": 0,
              "tipo_imovel": "CASA",
              "horas_alto_consumo": 8
            }
            """;

        mockMvc.perform(post("/test/analise-energetica")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value(
                "consumo_kwh: O consumo deve ser um valor positivo; quantidade_equipamentos: Deve haver pelo menos 1 equipamento registrado"
            ))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Retorna 400 ENUM_TYPE_ERROR para enum inválido")
    void shouldReturnEnumTypeError() throws Exception {
        mockMvc.perform(post("/test/analise-energetica")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "consumo_kwh": 420.0,
                      "uso_horario_pico": true,
                      "quantidade_equipamentos": 10,
                      "tipo_imovel": "GALPAO",
                      "horas_alto_consumo": 8
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("ENUM_TYPE_ERROR"))
            .andExpect(jsonPath("$.message").value(
                "Valor inválido. Aceitos: [CASA, APARTAMENTO, COMERCIO, ESCRITORIO, INDUSTRIA, OUTRO]"
            ))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Retorna 400 INVALID_TYPE_ERROR para tipo incompatível no JSON")
    void shouldReturnInvalidTypeError() throws Exception {
        mockMvc.perform(post("/test/analise-energetica")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "consumo_kwh": 420.0,
                      "uso_horario_pico": true,
                      "quantidade_equipamentos": "abc",
                      "tipo_imovel": "CASA",
                      "horas_alto_consumo": 8
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("INVALID_TYPE_ERROR"))
            .andExpect(jsonPath("$.message").value("Campo quantidade_equipamentos possui tipo inválido"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Retorna 400 HTTP_MESSAGE_ERROR para JSON malformado")
    void shouldReturnHttpMessageErrorForMalformedJson() throws Exception {
        mockMvc.perform(post("/test/analise-energetica")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"consumo_kwh\":420,"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("HTTP_MESSAGE_ERROR"))
            .andExpect(jsonPath("$.message").value("Corpo da requisição inválido"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Retorna 404 NOT_FOUND_ERROR para ResourceNotFoundException")
    void shouldReturnNotFoundErrorForResourceNotFoundException() throws Exception {
        mockMvc.perform(get("/test/resource-not-found"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("NOT_FOUND_ERROR"))
            .andExpect(jsonPath("$.message").value("Análise não encontrada"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Retorna 404 NOT_FOUND_ERROR para rota inexistente")
    void shouldReturnNotFoundErrorForUnknownRoute() throws Exception {
        mockMvc.perform(get("/rota-inexistente"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("NOT_FOUND_ERROR"))
            .andExpect(jsonPath("$.message").value("Rota não encontrada"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Retorna 405 METHOD_NOT_ALLOWED_ERROR para método não suportado")
    void shouldReturnMethodNotAllowedError() throws Exception {
        mockMvc.perform(get("/test/analise-energetica"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(405))
            .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED_ERROR"))
            .andExpect(jsonPath("$.message").value("Método HTTP não suportado"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Retorna 415 UNSUPPORTED_MEDIA_TYPE_ERROR para media type não suportado")
    void shouldReturnUnsupportedMediaTypeError() throws Exception {
        mockMvc.perform(post("/test/analise-energetica")
                .contentType(MediaType.TEXT_PLAIN)
                .content(validRequestJson()))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(415))
            .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE_ERROR"))
            .andExpect(jsonPath("$.message").value("Media type não suportado"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Retorna 400 BAD_REQUEST_ERROR para parâmetro obrigatório ausente")
    void shouldReturnBadRequestErrorForMissingRequestParam() throws Exception {
        mockMvc.perform(get("/test/required-param"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("BAD_REQUEST_ERROR"))
            .andExpect(jsonPath("$.message").value("Parâmetro obrigatório ausente: pagina"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Retorna 400 BAD_REQUEST_ERROR para query parameter com tipo inválido")
    void shouldReturnBadRequestErrorForInvalidQueryParameterType() throws Exception {
        mockMvc.perform(get("/test/required-param").param("pagina", "abc"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("BAD_REQUEST_ERROR"))
            .andExpect(jsonPath("$.message").value("Parâmetro pagina possui valor inválido"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Retorna 400 BAD_REQUEST_ERROR para path parameter com tipo inválido")
    void shouldReturnBadRequestErrorForInvalidPathParameterType() throws Exception {
        mockMvc.perform(get("/test/path-param/abc"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("BAD_REQUEST_ERROR"))
            .andExpect(jsonPath("$.message").value("Parâmetro id possui valor inválido"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Retorna 500 INTERNAL_ERROR sem expor detalhes internos")
    void shouldHideInternalExceptionDetails() throws Exception {
        mockMvc.perform(get("/test/unexpected-error"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value("Erro interno no servidor"))
            .andExpect(jsonPath("$.message", not(containsString("segredo-interno-42"))))
            .andExpect(content().string(not(containsString("segredo-interno-42"))))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    private String validRequestJson() {
        return """
            {
              "consumo_kwh": 420.0,
              "uso_horario_pico": true,
              "quantidade_equipamentos": 10,
              "tipo_imovel": "CASA",
              "horas_alto_consumo": 8
            }
            """;
    }
}
