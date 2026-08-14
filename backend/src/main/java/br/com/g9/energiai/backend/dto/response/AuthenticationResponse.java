package br.com.g9.energiai.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuthenticationResponse", description = "Resposta de autenticação bem-sucedida")
public record AuthenticationResponse(
        @JsonProperty("access_token")
        @Schema(description = "JWT de curta duração para chamadas protegidas com Bearer.",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.assinatura")
        String accessToken,

        @JsonProperty("token_type")
        @Schema(description = "Tipo de autenticação a usar no header Authorization.", example = "Bearer")
        String tokenType,

        @JsonProperty("expires_in")
        @Schema(description = "Tempo de validade do access token, em segundos.", example = "900")
        Long expiresIn,

        @Schema(description = "Dados públicos do usuário autenticado.")
        AuthenticatedUserResponse usuario
) {
}
