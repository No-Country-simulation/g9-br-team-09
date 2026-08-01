package br.com.g9.energiai.backend.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    private final JwtProperties properties;

    public JwtConfig(JwtProperties properties) {
        this.properties = properties;
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        byte[] secretBytes = decodeAndValidateSecret(properties.secret());
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretBytes));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        byte[] secretBytes = decodeAndValidateSecret(properties.secret());
        SecretKeySpec secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");

        return NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private byte[] decodeAndValidateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("O segredo JWT não pode estar vazio");
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("O segredo JWT deve estar em formato Base64 válido", e);
        }

        if (decoded.length < 32) {
            throw new IllegalArgumentException("O segredo JWT deve possuir pelo menos 256 bits (32 bytes) após a decodificação");
        }

        return decoded;
    }
}
