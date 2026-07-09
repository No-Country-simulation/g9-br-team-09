package br.com.g9.energiai.backend.dto.response;

import java.time.LocalDateTime;

public record ApiErrorResponse(
        LocalDateTime timestamp,
        Integer status,
        String error,
        String message
) {
}
