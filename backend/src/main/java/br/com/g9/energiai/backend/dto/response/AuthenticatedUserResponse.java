package br.com.g9.energiai.backend.dto.response;

import br.com.g9.energiai.backend.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "AuthenticatedUserResponse", description = "Dados do usuário autenticado")
public record AuthenticatedUserResponse(
        Long id,
        String nome,
        String email,
        UserRole role,
        @JsonProperty("criado_em")
        LocalDateTime criadoEm
) {
}
