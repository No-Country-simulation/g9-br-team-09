package br.com.g9.energiai.backend.support;

import br.com.g9.energiai.backend.config.JwtProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Produz tokens HS256 deliberadamente modificados para cenários negativos de integração.
 * Tokens válidos devem ser emitidos por {@code JwtTokenService}.
 */
public final class TestJwtFactory {

    private static final List<String> USER_ROLE = List.of("USER");

    private final JwtProperties jwtProperties;

    public TestJwtFactory(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String expiredFor(String subject) throws JOSEException {
        Instant now = Instant.now();
        TestJwtSpec spec = defaultSpec(subject, USER_ROLE);
        return build(new TestJwtSpec(
                spec.subject(), spec.roles(), now.minusSeconds(120), now.minusSeconds(60),
                spec.issuer(), spec.audience(), spec.signingSecret()
        ));
    }

    public String withInvalidSigningSecret(String subject, byte[] signingSecret) throws JOSEException {
        TestJwtSpec spec = defaultSpec(subject, USER_ROLE);
        return build(new TestJwtSpec(
                spec.subject(), spec.roles(), spec.issuedAt(), spec.expiresAt(),
                spec.issuer(), spec.audience(), signingSecret
        ));
    }

    public String withInvalidIssuer(String subject, String issuer) throws JOSEException {
        TestJwtSpec spec = defaultSpec(subject, USER_ROLE);
        return build(new TestJwtSpec(
                spec.subject(), spec.roles(), spec.issuedAt(), spec.expiresAt(),
                issuer, spec.audience(), spec.signingSecret()
        ));
    }

    public String withInvalidAudience(String subject, List<String> audience) throws JOSEException {
        TestJwtSpec spec = defaultSpec(subject, USER_ROLE);
        return build(new TestJwtSpec(
                spec.subject(), spec.roles(), spec.issuedAt(), spec.expiresAt(),
                spec.issuer(), audience, spec.signingSecret()
        ));
    }

    public String withSubject(String subject) throws JOSEException {
        return build(defaultSpec(subject, USER_ROLE));
    }

    public String withoutSubject() throws JOSEException {
        return build(defaultSpec(null, USER_ROLE));
    }

    public String withoutRoles(String subject) throws JOSEException {
        return build(defaultSpec(subject, null));
    }

    public String withRoles(String subject, List<String> roles) throws JOSEException {
        return build(defaultSpec(subject, roles));
    }

    public String build(TestJwtSpec spec) throws JOSEException {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(spec.issuer())
                .audience(spec.audience())
                .issueTime(Date.from(spec.issuedAt()))
                .expirationTime(Date.from(spec.expiresAt()));

        if (spec.subject() != null) {
            claims.subject(spec.subject());
        }
        if (spec.roles() != null) {
            claims.claim("roles", spec.roles());
        }

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(JOSEObjectType.JWT)
                .build();
        SignedJWT jwt = new SignedJWT(header, claims.build());
        jwt.sign(new MACSigner(spec.signingSecret()));
        return jwt.serialize();
    }

    private TestJwtSpec defaultSpec(String subject, List<String> roles) {
        Instant now = Instant.now();
        return new TestJwtSpec(
                subject,
                roles,
                now.minusSeconds(5),
                now.plusSeconds(900),
                jwtProperties.issuer(),
                List.of(jwtProperties.audience()),
                configuredSigningSecret()
        );
    }

    private byte[] configuredSigningSecret() {
        String secret = jwtProperties.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("O segredo JWT de teste deve ser Base64 não vazio");
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(secret.trim());
            if (decoded.length < 32) {
                throw new IllegalStateException("O segredo JWT de teste deve possuir pelo menos 32 bytes");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("O segredo JWT de teste deve estar em Base64 válido", exception);
        }
    }

    public record TestJwtSpec(
            String subject,
            List<String> roles,
            Instant issuedAt,
            Instant expiresAt,
            String issuer,
            List<String> audience,
            byte[] signingSecret
    ) {
        public TestJwtSpec {
            issuedAt = Objects.requireNonNull(issuedAt, "TestJwtSpec.issuedAt não pode ser nulo");
            expiresAt = Objects.requireNonNull(expiresAt, "TestJwtSpec.expiresAt não pode ser nulo");
            issuer = Objects.requireNonNull(issuer, "TestJwtSpec.issuer não pode ser nulo");
            audience = Objects.requireNonNull(audience, "TestJwtSpec.audience não pode ser nulo");
            signingSecret = Objects.requireNonNull(signingSecret, "TestJwtSpec.signingSecret não pode ser nulo");
            roles = roles == null ? null : List.copyOf(roles);
            audience = List.copyOf(audience);
            signingSecret = signingSecret.clone();
        }

        @Override
        public byte[] signingSecret() {
            return signingSecret.clone();
        }
    }
}
