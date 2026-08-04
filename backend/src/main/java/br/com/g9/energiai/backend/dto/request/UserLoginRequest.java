package br.com.g9.energiai.backend.dto.request;

import br.com.g9.energiai.backend.util.EmailNormalizer;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "UserLoginRequest", description = "Credenciais de acesso")
public record UserLoginRequest(
        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "Formato de e-mail inválido")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres")
        @Schema(description = "E-mail cadastrado", example = "lucas@email.com")
        String email,

        @NotBlank(message = "A senha é obrigatória")
        @Size(max = 100, message = "A senha deve ter no máximo 100 caracteres")
        @JsonProperty("senha")
        @Schema(description = "Senha de acesso", example = "senha-segura")
        String password
) {
        public UserLoginRequest {
                email = EmailNormalizer.normalize(email);
        }
}
