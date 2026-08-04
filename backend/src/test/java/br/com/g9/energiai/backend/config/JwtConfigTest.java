package br.com.g9.energiai.backend.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtConfigTest {

    @Test
    void shouldFailWhenSecretIsMissing() {
        JwtProperties properties = new JwtProperties(null, "iss", "aud", Duration.ofMinutes(15));
        JwtConfig config = new JwtConfig(properties);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, config::jwtEncoder);
        assertEquals("O segredo JWT não foi fornecido. Certifique-se de definir a variável de ambiente JWT_SECRET.", exception.getMessage());
    }

    @Test
    void shouldFailWhenSecretIsTooShort() {
        String shortSecret = "YmFkLXNlY3JldA==";
        JwtProperties properties = new JwtProperties(shortSecret, "iss", "aud", Duration.ofMinutes(15));
        JwtConfig config = new JwtConfig(properties);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, config::jwtEncoder);
        assertEquals("O segredo JWT deve possuir pelo menos 256 bits (32 bytes) após a decodificação.", exception.getMessage());
    }

    @Test
    void shouldFailWhenSecretIsNotValidBase64() {
        String invalidBase64 = "not-a-base64-string!";
        JwtProperties properties = new JwtProperties(invalidBase64, "iss", "aud", Duration.ofMinutes(15));
        JwtConfig config = new JwtConfig(properties);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, config::jwtEncoder);
        assertEquals("O segredo JWT deve estar em formato Base64 válido. Verifique se há caracteres inválidos (como '$' ou espaços).", exception.getMessage());
    }

    @Test
    void shouldSucceedWhenSecretIsParamsAreCorrect() {
        String validSecret = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=";
        JwtProperties properties = new JwtProperties(validSecret, "iss", "aud", Duration.ofMinutes(15));
        JwtConfig config = new JwtConfig(properties);

        assertDoesNotThrow(config::jwtEncoder);
        assertDoesNotThrow(config::jwtDecoder);
    }
}
