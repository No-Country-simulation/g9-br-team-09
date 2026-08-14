package br.com.g9.energiai.backend.dto.request;

import br.com.g9.energiai.backend.util.EmailNormalizer;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "UserRegistrationRequest", description = "Dados para cadastro de novo usuário")
public record UserRegistrationRequest(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 255, message = "O nome deve ter no máximo 255 caracteres")
        @Schema(description = "Nome completo do usuário", example = "Lucas Rossoni")
        String nome,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "Formato de e-mail inválido")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres")
        @Schema(description = "E-mail único do usuário. Espaços nas extremidades e maiúsculas são normalizados.",
                example = "lucas@email.com")
        String email,

        @NotBlank(message = "A senha é obrigatória")
        @Size(min = 8, message = "A senha deve ter no mínimo 8 caracteres")
        @Size(max = 100, message = "A senha deve ter no máximo 100 caracteres")
        @JsonProperty("senha")
        @Schema(description = "Senha de acesso, com 8 a 100 caracteres", example = "senha-segura",
                format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        String password
) {
        public UserRegistrationRequest {
                nome = nome != null ? nome.trim() : null;
                email = EmailNormalizer.normalize(email);
        }
}
