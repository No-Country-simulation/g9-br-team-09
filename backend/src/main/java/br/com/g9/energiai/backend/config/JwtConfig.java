package br.com.g9.energiai.backend.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;

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

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(properties.issuer());

        OAuth2TokenValidator<Jwt> withAudience = new JwtClaimValidator<Object>(
                JwtClaimNames.AUD,
                aud -> {
                    if (aud instanceof String s) {
                        return properties.audience().equals(s);
                    } else if (aud instanceof List<?> l) {
                        return l.contains(properties.audience());
                    }
                    return false;
                }
        );

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));

        return decoder;
    }

    private byte[] decodeAndValidateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("O segredo JWT não pode estar vazio. Verifique a variável de ambiente JWT_SECRET.");
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("O segredo JWT deve estar em formato Base64 válido. Verifique se há caracteres inválidos (como '$' ou espaços).", e);
        }

        if (decoded.length < 32) {
            throw new IllegalArgumentException("O segredo JWT deve possuir pelo menos 256 bits (32 bytes) após a decodificação.");
        }

        return decoded;
    }
}
