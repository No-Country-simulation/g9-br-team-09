package br.com.g9.energiai.backend.controller;

import br.com.g9.energiai.backend.client.ml.MlPredictionClient;
import br.com.g9.energiai.backend.client.ml.exception.MlPredictionClientException;
import br.com.g9.energiai.backend.entity.RefreshTokenEntity;
import br.com.g9.energiai.backend.enums.RefreshTokenRevocationReason;
import br.com.g9.energiai.backend.repository.EnergyAnalysisRepository;
import br.com.g9.energiai.backend.repository.RefreshTokenRepository;
import br.com.g9.energiai.backend.repository.UserRepository;
import br.com.g9.energiai.backend.service.RefreshTokenHasher;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityFlowIntegrationTest {

    private static final String CONTEXT_PATH = "/api/v1";
    private static final String PASSWORD = "senha-segura";
    private static final Set<String> FORBIDDEN_PUBLIC_FIELDS = Set.of(
            "password",
            "password_hash",
            "passwordHash",
            "refresh_token",
            "refreshToken",
            "token_hash",
            "tokenHash",
            "user_id",
            "userId",
            "owner",
            "proprietario",
            "family",
            "family_id",
            "familyId"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EnergyAnalysisRepository energyAnalysisRepository;

    @Autowired
    private RefreshTokenHasher refreshTokenHasher;

    @MockitoBean
    private MlPredictionClient mlPredictionClient;

    private final Set<String> testEmails = new HashSet<>();

    @BeforeEach
    void configureMlFallback() {
        when(mlPredictionClient.predict(any()))
                .thenThrow(new MlPredictionClientException("ML indisponível no teste de segurança"));
    }

    @AfterEach
    void cleanUpTestData() {
        for (String email : testEmails) {
            userRepository.findByEmail(email).ifPresent(user -> {
                List<RefreshTokenEntity> tokens = refreshTokenRepository.findAll().stream()
                        .filter(token -> user.getId().equals(token.getUser().getId()))
                        .toList();
                tokens.forEach(token -> token.setReplacedByToken(null));
                refreshTokenRepository.saveAllAndFlush(tokens);
                refreshTokenRepository.deleteAllByUserId(user.getId());
                var analyses = energyAnalysisRepository
                        .findAllByUserIdOrderByCreatedAtDesc(user.getId(), Pageable.unpaged())
                        .getContent();
                energyAnalysisRepository.deleteAll(analyses);
                energyAnalysisRepository.flush();
                userRepository.delete(user);
                userRepository.flush();
            });
        }
    }

    @Test
    void shouldCompleteAuthenticationAuthorizationRotationAndLogoutFlow() throws Exception {
        String email = "security-flow-primary@example.test";
        register(email);

        Session initialSession = login(email);
        Jwt initialAccessToken = jwtDecoder.decode(initialSession.accessToken());
        assertNotNull(initialAccessToken.getId());
        assertFalse(initialAccessToken.getId().isBlank());

        MvcResult creation = mockMvc.perform(post("/api/v1/analise-energetica")
                        .contextPath(CONTEXT_PATH)
                        .header("Authorization", "Bearer " + initialSession.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAnalysisRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();
        assertNoSensitiveFields(creation);
        long analysisId = json(creation).get("id").asLong();

        MvcResult history = mockMvc.perform(get("/api/v1/analise-energetica")
                        .contextPath(CONTEXT_PATH)
                        .header("Authorization", "Bearer " + initialSession.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elementos").value(1))
                .andExpect(jsonPath("$.analises[0].id").value(analysisId))
                .andReturn();
        assertNoSensitiveFields(history);

        MvcResult detail = mockMvc.perform(get("/api/v1/analise-energetica/{id}", analysisId)
                        .contextPath(CONTEXT_PATH)
                        .header("Authorization", "Bearer " + initialSession.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(analysisId))
                .andReturn();
        assertNoSensitiveFields(detail);

        MvcResult summary = mockMvc.perform(get("/api/v1/analise-energetica/resumo")
                        .contextPath(CONTEXT_PATH)
                        .header("Authorization", "Bearer " + initialSession.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_analises").value(1))
                .andReturn();
        assertNoSensitiveFields(summary);

        MvcResult refresh = refreshSuccessfully(initialSession);
        String rotatedAccessTokenValue = json(refresh).get("access_token").stringValue();
        String rotatedRefreshToken = cookieValue(refresh, "refresh_token");
        assertNotNull(rotatedRefreshToken);
        assertNotEquals(initialSession.refreshToken(), rotatedRefreshToken);
        Jwt rotatedAccessToken = jwtDecoder.decode(rotatedAccessTokenValue);
        assertNotNull(rotatedAccessToken.getId());
        assertFalse(rotatedAccessToken.getId().isBlank());
        assertNotEquals(initialAccessToken.getId(), rotatedAccessToken.getId());

        RefreshTokenEntity predecessor = tokenForRawValue(initialSession.refreshToken());
        RefreshTokenEntity successor = tokenForRawValue(rotatedRefreshToken);
        assertNotNull(predecessor.getRevokedAt());
        assertEquals(RefreshTokenRevocationReason.ROTATED, predecessor.getRevocationReason());
        assertNotNull(predecessor.getReplacedByToken());
        assertEquals(successor.getId(), predecessor.getReplacedByToken().getId());
        assertEquals(predecessor.getFamilyId(), successor.getFamilyId());
        assertNull(successor.getRevokedAt());
        assertNull(successor.getRevocationReason());
        assertStoredOnlyAsHash(predecessor, initialSession.refreshToken());
        assertStoredOnlyAsHash(successor, rotatedRefreshToken);

        String responseCsrfCookie = cookieValue(refresh, "XSRF-TOKEN");
        String effectiveCsrfCookie = responseCsrfCookie != null
                ? responseCsrfCookie
                : initialSession.csrfCookie();
        String rotatedCsrfHeader = csrfHeader(refresh);
        assertNotNull(effectiveCsrfCookie);
        assertNotNull(rotatedCsrfHeader);
        assertEquals(effectiveCsrfCookie, rotatedCsrfHeader);
        Session rotatedSession = new Session(
                rotatedAccessTokenValue,
                rotatedRefreshToken,
                effectiveCsrfCookie,
                rotatedCsrfHeader
        );
        MvcResult logout = logout(rotatedSession);
        assertCookieCleared(logout, "refresh_token");
        Cookie clearedCsrf = logout.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(clearedCsrf);
        assertEquals(0, clearedCsrf.getMaxAge());

        RefreshTokenEntity loggedOutSuccessor = tokenForRawValue(rotatedRefreshToken);
        assertNotNull(loggedOutSuccessor.getRevokedAt());
        assertEquals(RefreshTokenRevocationReason.LOGOUT, loggedOutSuccessor.getRevocationReason());

        MvcResult postLogoutRefresh = refreshUnauthorized(rotatedSession);
        assertGenericRefreshError(postLogoutRefresh);
        assertNoSensitiveFields(postLogoutRefresh);
    }

    @Test
    void shouldIsolateIndependentRefreshTokenFamiliesForTheSameUser() throws Exception {
        String email = "security-flow-sessions@example.test";
        register(email);
        Session familyASession = login(email);
        Session familyBSession = login(email);

        assertNotEquals(familyASession.refreshToken(), familyBSession.refreshToken());
        RefreshTokenEntity familyAToken = tokenForRawValue(familyASession.refreshToken());
        RefreshTokenEntity familyBToken = tokenForRawValue(familyBSession.refreshToken());
        assertNotEquals(familyAToken.getFamilyId(), familyBToken.getFamilyId());
        String familyAId = familyAToken.getFamilyId();
        String familyBId = familyBToken.getFamilyId();
        assertStoredOnlyAsHash(familyAToken, familyASession.refreshToken());
        assertStoredOnlyAsHash(familyBToken, familyBSession.refreshToken());

        logout(familyASession);
        RefreshTokenEntity loggedOutFamilyA = tokenForRawValue(familyASession.refreshToken());
        assertEquals(RefreshTokenRevocationReason.LOGOUT, loggedOutFamilyA.getRevocationReason());
        assertNotNull(loggedOutFamilyA.getRevokedAt());

        RefreshTokenEntity unchangedFamilyB = tokenForRawValue(familyBSession.refreshToken());
        assertNull(unchangedFamilyB.getRevokedAt());
        assertNull(unchangedFamilyB.getRevocationReason());

        MvcResult rejectedFamilyAReuse = refreshUnauthorized(familyASession);
        assertGenericRefreshError(rejectedFamilyAReuse);
        assertNoSensitiveFields(rejectedFamilyAReuse);

        unchangedFamilyB = tokenForRawValue(familyBSession.refreshToken());
        assertNull(unchangedFamilyB.getRevokedAt());
        assertNull(unchangedFamilyB.getRevocationReason());

        MvcResult familyBRefresh = refreshSuccessfully(familyBSession);
        String familyBSuccessorRaw = cookieValue(familyBRefresh, "refresh_token");
        assertNotNull(familyBSuccessorRaw);
        assertNotEquals(familyBSession.refreshToken(), familyBSuccessorRaw);
        Jwt familyBAccessToken = jwtDecoder.decode(json(familyBRefresh).get("access_token").stringValue());
        assertNotNull(familyBAccessToken.getId());
        assertFalse(familyBAccessToken.getId().isBlank());

        RefreshTokenEntity rotatedFamilyB = tokenForRawValue(familyBSession.refreshToken());
        RefreshTokenEntity familyBSuccessor = tokenForRawValue(familyBSuccessorRaw);
        assertEquals(RefreshTokenRevocationReason.ROTATED, rotatedFamilyB.getRevocationReason());
        assertNotNull(rotatedFamilyB.getRevokedAt());
        assertEquals(familyBId, familyBSuccessor.getFamilyId());
        assertNull(familyBSuccessor.getRevokedAt());
        assertNull(familyBSuccessor.getRevocationReason());
        assertStoredOnlyAsHash(familyBSuccessor, familyBSuccessorRaw);

        List<RefreshTokenEntity> familyA = refreshTokenRepository.findAllByFamilyIdOrderById(familyAId);
        assertEquals(1, familyA.size());
        assertEquals(RefreshTokenRevocationReason.LOGOUT, familyA.getFirst().getRevocationReason());
        assertEquals(2, refreshTokenRepository.countByFamilyId(familyBId));
    }

    private MvcResult register(String email) throws Exception {
        testEmails.add(email);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contextPath(CONTEXT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Usuário do fluxo de segurança",
                                  "email": "%s",
                                  "senha": "%s"
                                }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andReturn();
        assertNoSensitiveFields(result);
        return result;
    }

    private Session login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contextPath(CONTEXT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "senha": "%s"
                                }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isString())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andReturn();
        assertNoSensitiveFields(result);

        String refreshToken = cookieValue(result, "refresh_token");
        String csrfCookie = cookieValue(result, "XSRF-TOKEN");
        String csrfHeader = csrfHeader(result);
        assertNotNull(refreshToken);
        assertNotNull(csrfCookie);
        assertNotNull(csrfHeader);
        assertEquals(csrfCookie, csrfHeader);
        return new Session(json(result).get("access_token").stringValue(), refreshToken, csrfCookie, csrfHeader);
    }

    private MvcResult refreshSuccessfully(Session session) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contextPath(CONTEXT_PATH)
                        .cookie(new Cookie("refresh_token", session.refreshToken()),
                                new Cookie("XSRF-TOKEN", session.csrfCookie()))
                        .header("X-XSRF-TOKEN", session.csrfHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isString())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andReturn();
        assertNoSensitiveFields(result);
        return result;
    }

    private MvcResult refreshUnauthorized(Session session) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                        .contextPath(CONTEXT_PATH)
                        .cookie(new Cookie("refresh_token", session.refreshToken()),
                                new Cookie("XSRF-TOKEN", session.csrfCookie()))
                        .header("X-XSRF-TOKEN", session.csrfHeader()))
                .andExpect(status().isUnauthorized())
                .andReturn();
    }

    private MvcResult logout(Session session) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/logout")
                        .contextPath(CONTEXT_PATH)
                        .cookie(new Cookie("refresh_token", session.refreshToken()),
                                new Cookie("XSRF-TOKEN", session.csrfCookie()))
                        .header("X-XSRF-TOKEN", session.csrfHeader()))
                .andExpect(status().isNoContent())
                .andReturn();
    }

    private RefreshTokenEntity tokenForRawValue(String rawToken) {
        String tokenHash = refreshTokenHasher.hash(rawToken);
        return refreshTokenRepository.findAll().stream()
                .filter(token -> tokenHash.equals(token.getTokenHash()))
                .findFirst()
                .orElseThrow();
    }

    private void assertStoredOnlyAsHash(RefreshTokenEntity token, String rawToken) {
        assertEquals(refreshTokenHasher.hash(rawToken), token.getTokenHash());
        assertNotEquals(rawToken, token.getTokenHash());
    }

    private void assertGenericRefreshError(MvcResult result) throws Exception {
        JsonNode body = json(result);
        assertEquals(401, result.getResponse().getStatus());
        assertEquals("UNAUTHORIZED_ERROR", body.get("error").stringValue());
        assertEquals("Não foi possível renovar a sessão", body.get("message").stringValue());
        assertEquals(Set.of("timestamp", "status", "error", "message"), fieldNames(body));
    }

    private void assertCookieCleared(MvcResult result, String name) {
        assertTrue(result.getResponse().getHeaders("Set-Cookie").stream()
                .anyMatch(value -> value.startsWith(name + "=") && value.contains("Max-Age=0")));
    }

    private void assertNoSensitiveFields(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString();
        if (!content.isBlank()) {
            assertNoSensitiveFields(json(result), "$");
        }
    }

    private void assertNoSensitiveFields(JsonNode node, String path) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> property : node.properties()) {
                assertFalse(FORBIDDEN_PUBLIC_FIELDS.contains(property.getKey()),
                        () -> "Campo interno exposto em " + path + ": " + property.getKey());
                assertNoSensitiveFields(property.getValue(), path + "." + property.getKey());
            }
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                assertNoSensitiveFields(node.get(index), path + "[" + index + "]");
            }
        }
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.properties().forEach(property -> names.add(property.getKey()));
        return names;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String csrfHeader(MvcResult result) {
        return result.getResponse().getHeader("X-XSRF-TOKEN");
    }

    private String cookieValue(MvcResult result, String name) {
        Cookie responseCookie = result.getResponse().getCookie(name);
        if (responseCookie != null) {
            return responseCookie.getValue();
        }
        Collection<String> headers = result.getResponse().getHeaders("Set-Cookie");
        return headers.stream()
                .filter(value -> value.startsWith(name + "="))
                .map(value -> value.substring(name.length() + 1))
                .map(value -> value.contains(";") ? value.substring(0, value.indexOf(';')) : value)
                .findFirst()
                .orElse(null);
    }

    private String validAnalysisRequest() {
        return """
                {
                  "consumo_kwh": 500,
                  "uso_horario_pico": true,
                  "quantidade_equipamentos": 10,
                  "tipo_imovel": "CASA",
                  "horas_alto_consumo": 8
                }
                """;
    }

    private record Session(String accessToken, String refreshToken, String csrfCookie, String csrfHeader) {
    }
}
