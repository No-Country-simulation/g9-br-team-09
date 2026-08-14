package br.com.g9.energiai.backend.dto.response;

import br.com.g9.energiai.backend.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "UserRegistrationResponse", description = "Resposta de confirmação de cadastro")
public record UserRegistrationResponse(
        @Schema(description = "Identificador único do usuário criado.", example = "1")
        Long id,
        @Schema(description = "Nome completo informado no cadastro.", example = "Lucas Rossoni")
        String nome,
        @Schema(description = "E-mail normalizado do usuário criado.", example = "lucas@email.com")
        String email,
        @Schema(description = "Papel atribuído ao usuário no cadastro.", example = "USER")
        UserRole role,
        @JsonProperty("criado_em")
        @Schema(description = "Data e hora de criação do usuário.", example = "2026-08-14T13:00:00")
        LocalDateTime criadoEm
) {
}
