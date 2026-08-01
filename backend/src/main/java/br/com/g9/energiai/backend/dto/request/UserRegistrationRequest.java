package br.com.g9.energiai.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "UserRegistrationRequest", description = "Dados para cadastro de novo usuário")
public record UserRegistrationRequest(
        @NotBlank(message = "O nome é obrigatório")
        @Schema(description = "Nome completo do usuário", example = "Lucas Rossoni")
        String nome,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "Formato de e-mail inválido")
        @Schema(description = "E-mail único do usuário", example = "lucas@email.com")
        String email,

        @NotBlank(message = "A senha é obrigatória")
        @Size(min = 8, message = "A senha deve ter no mínimo 8 caracteres")
        @JsonProperty("senha")
        @Schema(description = "Senha de acesso", example = "senha-segura")
        String password
) {
}
