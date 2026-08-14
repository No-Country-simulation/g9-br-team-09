package br.com.g9.energiai.backend.dto.response;

import br.com.g9.energiai.backend.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "AuthenticatedUserResponse", description = "Dados do usuário autenticado")
public record AuthenticatedUserResponse(
        @Schema(description = "Identificador único do usuário autenticado.", example = "1")
        Long id,
        @Schema(description = "Nome completo do usuário.", example = "Lucas Rossoni")
        String nome,
        @Schema(description = "E-mail normalizado do usuário.", example = "lucas@email.com")
        String email,
        @Schema(description = "Papel do usuário autenticado.", example = "USER")
        UserRole role,
        @JsonProperty("criado_em")
        @Schema(description = "Data e hora de criação do usuário.", example = "2026-08-14T13:00:00")
        LocalDateTime criadoEm
) {
}
