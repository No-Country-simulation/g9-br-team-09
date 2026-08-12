package br.com.g9.energiai.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "ApiErrorResponse", description = "Resposta padronizada para erros da API")
public record ApiErrorResponse(
    @Schema(description = "Data e hora em que o erro foi gerado.", example = "2026-07-10T18:30:00")
    LocalDateTime timestamp,

    @Schema(description = "Código HTTP retornado pela API.", example = "400")
    Integer status,

    @Schema(description = "Código resumido do tipo de erro.", example = "VALIDATION_ERROR")
    String error,

    @Schema(
        description = "Mensagem pública com o motivo do erro.",
        example = "consumo_kwh: O consumo deve ser um valor positivo"
    )
    String message
) {
}
