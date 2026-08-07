package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.config.JwtProperties;
import br.com.g9.energiai.backend.entity.AppUser;
import br.com.g9.energiai.backend.support.TestUserFixtures;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import java.text.ParseException;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class JwtTokenServiceIntegrationTest {

    private static final Set<String> SENSITIVE_CLAIMS = Set.of(
            "password",
            "password_hash",
            "passwordHash",
            "refresh_token",
            "refreshToken",
            "token_hash",
            "tokenHash",
            "email",
            "user"
    );

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void shouldIssueAndValidateCompleteAccessTokenContract() throws ParseException {
        AppUser user = TestUserFixtures.nonPersistedActiveUser(117L);

        String token = jwtTokenService.generateToken(user);
        SignedJWT signedJwt = SignedJWT.parse(token);
        Jwt decoded = jwtDecoder.decode(token);

        assertEquals(JWSAlgorithm.HS256, signedJwt.getHeader().getAlgorithm());
        if (signedJwt.getHeader().getType() != null) {
            assertEquals(JOSEObjectType.JWT, signedJwt.getHeader().getType());
        }

        assertEquals(user.getId().toString(), decoded.getSubject());
        assertEquals(jwtProperties.issuer(), decoded.getClaimAsString(JwtClaimNames.ISS));
        assertTrue(decoded.getAudience().contains(jwtProperties.audience()));
        assertEquals(List.of("USER"), decoded.getClaimAsStringList("roles"));
        assertNotNull(decoded.getId());
        assertFalse(decoded.getId().isBlank());
        assertNotNull(decoded.getIssuedAt());
        assertNotNull(decoded.getExpiresAt());
        assertTrue(decoded.getExpiresAt().isAfter(decoded.getIssuedAt()));
        assertEquals(
                jwtProperties.accessTokenExpiration(),
                Duration.between(decoded.getIssuedAt(), decoded.getExpiresAt())
        );

        SENSITIVE_CLAIMS.forEach(claim -> assertFalse(decoded.hasClaim(claim),
                () -> "Access token must not contain sensitive claim: " + claim));
        assertFalse(decoded.getClaims().keySet().stream()
                .map(String::toLowerCase)
                .anyMatch(claim -> claim.contains("family")),
                "Access token must not contain refresh-token family information");
    }

    @Test
    void shouldIssueUniqueTokenIdentifiersForTheSameUser() {
        AppUser user = TestUserFixtures.nonPersistedActiveUser(117L);

        Jwt first = jwtDecoder.decode(jwtTokenService.generateToken(user));
        Jwt second = jwtDecoder.decode(jwtTokenService.generateToken(user));

        assertNotNull(first.getId());
        assertFalse(first.getId().isBlank());
        assertNotNull(second.getId());
        assertFalse(second.getId().isBlank());
        assertNotEquals(first.getId(), second.getId());
    }
}
