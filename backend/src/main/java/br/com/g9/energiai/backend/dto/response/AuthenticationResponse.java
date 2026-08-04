package br.com.g9.energiai.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuthenticationResponse", description = "Resposta de autenticação bem-sucedida")
public record AuthenticationResponse(
        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("token_type")
        String tokenType,

        @JsonProperty("expires_in")
        Long expiresIn,

        AuthenticatedUserResponse usuario
) {
}
