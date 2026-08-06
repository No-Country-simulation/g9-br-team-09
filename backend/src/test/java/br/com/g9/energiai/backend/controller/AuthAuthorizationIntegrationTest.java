package br.com.g9.energiai.backend.controller;

import br.com.g9.energiai.backend.config.JwtProperties;
import br.com.g9.energiai.backend.entity.AppUser;
import br.com.g9.energiai.backend.enums.UserRole;
import br.com.g9.energiai.backend.repository.RefreshTokenRepository;
import br.com.g9.energiai.backend.repository.UserRepository;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAuthorizationIntegrationTest {

    private static final String ERROR_CONTENT_TYPE = "application/json;charset=UTF-8";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAll();
    }

    @Test
    void shouldAllowUserRoleToAccessMe() throws Exception {
        AppUser user = saveUser(true);

        mockMvc.perform(authenticatedMe(token(user.getId().toString(), List.of("USER"),
                        Instant.now().plusSeconds(900), issuer(), audience(), signingSecret())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.getEmail()));
    }

    @Test
    void shouldReturnForbiddenWhenUserRoleIsMissing() throws Exception {
        ResultActions result = mockMvc.perform(authenticatedMe(token("1", List.of("ADMIN"),
                Instant.now().plusSeconds(900), issuer(), audience(), signingSecret())));

        expectApiError(result, 403, "FORBIDDEN_ERROR", "Acesso negado")
                .andExpect(content().contentType(ERROR_CONTENT_TYPE));
    }

    @Test
    void shouldReturnForbiddenWhenRolesClaimIsMissing() throws Exception {
        ResultActions result = mockMvc.perform(authenticatedMe(token("1", null,
                Instant.now().plusSeconds(900), issuer(), audience(), signingSecret())));

        expectApiError(result, 403, "FORBIDDEN_ERROR", "Acesso negado")
                .andExpect(content().contentType(ERROR_CONTENT_TYPE));
    }

    @Test
    void shouldReturnUnauthorizedWhenTokenIsMissing() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/v1/auth/me").contextPath("/api/v1"));

        expectApiError(result, 401, "UNAUTHORIZED_ERROR", "Token inválido ou ausente")
                .andExpect(content().contentType(ERROR_CONTENT_TYPE));
    }

    @Test
    void shouldReturnUnauthorizedWhenTokenIsInvalid() throws Exception {
        assertUnauthorized("not-a-jwt");
    }

    @Test
    void shouldReturnUnauthorizedWhenTokenIsExpired() throws Exception {
        assertUnauthorized(token("1", List.of("USER"), Instant.now().minusSeconds(60),
                issuer(), audience(), signingSecret()));
    }

    @Test
    void shouldReturnUnauthorizedWhenIssuerIsInvalid() throws Exception {
        assertUnauthorized(token("1", List.of("USER"), Instant.now().plusSeconds(900),
                "invalid-issuer", audience(), signingSecret()));
    }

    @Test
    void shouldReturnUnauthorizedWhenAudienceIsInvalid() throws Exception {
        assertUnauthorized(token("1", List.of("USER"), Instant.now().plusSeconds(900),
                issuer(), List.of("invalid-audience"), signingSecret()));
    }

    @Test
    void shouldReturnUnauthorizedWhenSignatureIsInvalid() throws Exception {
        byte[] invalidSecret = new byte[32];
        Arrays.fill(invalidSecret, (byte) 7);

        assertUnauthorized(token("1", List.of("USER"), Instant.now().plusSeconds(900),
                issuer(), audience(), invalidSecret));
    }

    @Test
    void shouldReturnUnauthorizedInsteadOfServerErrorWhenSubjectIsNotNumeric() throws Exception {
        ResultActions result = mockMvc.perform(authenticatedMe(token("not-a-number", List.of("USER"),
                Instant.now().plusSeconds(900), issuer(), audience(), signingSecret())));

        expectApiError(result, 401, "UNAUTHORIZED_ERROR", "Token com identificador inválido");
    }

    @Test
    void shouldReturnUnauthorizedWhenInactiveUserAccessesMe() throws Exception {
        AppUser user = saveUser(false);
        ResultActions result = mockMvc.perform(authenticatedMe(token(user.getId().toString(), List.of("USER"),
                Instant.now().plusSeconds(900), issuer(), audience(), signingSecret())));

        expectApiError(result, 401, "UNAUTHORIZED_ERROR", "Token inválido ou usuário inativo");
    }

    private void assertUnauthorized(String token) throws Exception {
        ResultActions result = mockMvc.perform(authenticatedMe(token));
        expectApiError(result, 401, "UNAUTHORIZED_ERROR", "Token inválido ou ausente");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticatedMe(String token) {
        return get("/api/v1/auth/me")
                .contextPath("/api/v1")
                .header("Authorization", "Bearer " + token);
    }

    private ResultActions expectApiError(ResultActions result, int expectedStatus, String error, String message)
            throws Exception {
        return result.andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").value(message));
    }

    private AppUser saveUser(boolean active) {
        return userRepository.save(AppUser.builder()
                .name("Authorization Test")
                .email("authorization@example.com")
                .passwordHash("unused-password-hash")
                .role(UserRole.USER)
                .active(active)
                .build());
    }

    private byte[] signingSecret() {
        return Base64.getDecoder().decode(jwtProperties.secret());
    }

    private String issuer() {
        return jwtProperties.issuer();
    }

    private List<String> audience() {
        return List.of(jwtProperties.audience());
    }

    private String token(String subject, List<String> roles, Instant expiresAt, String issuer,
                         List<String> audience, byte[] secret) throws JOSEException {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuer)
                .audience(audience)
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(expiresAt));

        if (roles != null) {
            claims.claim("roles", roles);
        }

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(JOSEObjectType.JWT)
                .build();
        SignedJWT jwt = new SignedJWT(header, claims.build());
        jwt.sign(new MACSigner(secret));
        return jwt.serialize();
    }
}
