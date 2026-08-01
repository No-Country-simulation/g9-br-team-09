package br.com.g9.energiai.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "UserLoginRequest", description = "Credenciais de acesso")
public record UserLoginRequest(
        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "Formato de e-mail inválido")
        @Schema(description = "E-mail cadastrado", example = "lucas@email.com")
        String email,

        @NotBlank(message = "A senha é obrigatória")
        @JsonProperty("senha")
        @Schema(description = "Senha de acesso", example = "senha-segura")
        String password
) {
}
