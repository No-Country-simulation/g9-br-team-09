package br.com.g9.energiai.backend.controller;

import br.com.g9.energiai.backend.config.RefreshTokenProperties;
import br.com.g9.energiai.backend.entity.RefreshTokenEntity;
import br.com.g9.energiai.backend.enums.RefreshTokenRevocationReason;
import br.com.g9.energiai.backend.repository.RefreshTokenRepository;
import br.com.g9.energiai.backend.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    private static final String PASSWORD = "senha-segura";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenProperties refreshTokenProperties;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAll();
    }

    @Test
    void shouldRegisterUserSuccessfully() throws Exception {
        String request = """
                {
                  "nome": "Lucas Rossoni",
                  "email": "lucas@email.com",
                  "senha": "senha-segura"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Lucas Rossoni"))
                .andExpect(jsonPath("$.email").value("lucas@email.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.criado_em").exists());
    }

    @Test
    void shouldReturnConflictWhenEmailAlreadyExists() throws Exception {
        String request = """
                {
                  "nome": "Lucas Rossoni",
                  "email": "lucas@email.com",
                  "senha": "senha-segura"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                .contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT_ERROR"))
                .andExpect(jsonPath("$.message").value("O e-mail informado já está em uso"));
    }

    @Test
    void shouldLoginSuccessfullyAndReturnToken() throws Exception {
        String registerRequest = """
                {
                  "nome": "Lucas",
                  "email": "lucas@email.com",
                  "senha": "senha-segura"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                .contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest));

        String loginRequest = """
                {
                  "email": "lucas@email.com",
                  "senha": "senha-segura"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.usuario.email").value("lucas@email.com"))
                .andExpect(jsonPath("$.refresh_token").doesNotExist())
                .andExpect(header().exists("X-XSRF-TOKEN"))
                .andReturn();

        assertNotNull(cookieValue(result, "refresh_token"));
        assertEquals(result.getResponse().getHeader("X-XSRF-TOKEN"), cookieValue(result, "XSRF-TOKEN"));
        assertTrue(setCookie(result, "refresh_token").contains("HttpOnly"));
        assertEquals(1, refreshTokenRepository.count());
    }

    @Test
    @DisplayName("Deve rotacionar o refresh token e manter o contrato JSON do login")
    void shouldRotateRefreshToken() throws Exception {
        register("rotacao@email.com");
        MvcResult login = login("rotacao@email.com");
        String oldRefresh = cookieValue(login, "refresh_token");
        String csrf = login.getResponse().getHeader("X-XSRF-TOKEN");

        MvcResult refresh = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contextPath("/api/v1")
                        .cookie(new Cookie("refresh_token", oldRefresh), new Cookie("XSRF-TOKEN", csrf))
                        .header("X-XSRF-TOKEN", csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.refresh_token").doesNotExist())
                .andReturn();

        String newRefresh = cookieValue(refresh, "refresh_token");
        assertNotNull(newRefresh);
        assertNotEquals(oldRefresh, newRefresh);
        assertEquals(2, refreshTokenRepository.count());
        var family = refreshTokenRepository.findAll();
        assertEquals(1, family.stream()
                .filter(token -> token.getRevocationReason() == RefreshTokenRevocationReason.ROTATED)
                .count());
        assertEquals(1, family.stream().filter(token -> token.getRevokedAt() == null).count());
    }

    @Test
    @DisplayName("Reuso dentro da tolerância deve retornar 401 sem apagar o cookie")
    void shouldRejectConcurrentReuseInsideGraceWithoutClearingCookie() throws Exception {
        register("grace@email.com");
        MvcResult login = login("grace@email.com");
        String oldRefresh = cookieValue(login, "refresh_token");
        String csrf = login.getResponse().getHeader("X-XSRF-TOKEN");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contextPath("/api/v1")
                        .cookie(new Cookie("refresh_token", oldRefresh), new Cookie("XSRF-TOKEN", csrf))
                        .header("X-XSRF-TOKEN", csrf))
                .andExpect(status().isOk());

        MvcResult reuse = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contextPath("/api/v1")
                        .cookie(new Cookie("refresh_token", oldRefresh), new Cookie("XSRF-TOKEN", csrf))
                        .header("X-XSRF-TOKEN", csrf))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_ERROR"))
                .andExpect(jsonPath("$.message").value("Não foi possível renovar a sessão"))
                .andReturn();

        assertFalse(reuse.getResponse().getHeaders("Set-Cookie").stream()
                .anyMatch(value -> value.startsWith("refresh_token=")));
    }

    @Test
    @DisplayName("Refresh e logout devem exigir CSRF mesmo sendo permitAll")
    void shouldRequireCsrfForRefreshAndLogout() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").contextPath("/api/v1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN_ERROR"));

        mockMvc.perform(post("/api/v1/auth/logout").contextPath("/api/v1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN_ERROR"));
    }

    @Test
    @DisplayName("Refresh sem header CSRF deve devolver o token do cookie para bootstrap")
    void shouldExposeCsrfTokenWhenRefreshHeaderIsMissing() throws Exception {
        register("csrf-bootstrap@email.com");
        MvcResult login = login("csrf-bootstrap@email.com");
        String csrf = login.getResponse().getHeader("X-XSRF-TOKEN");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contextPath("/api/v1")
                        .cookie(new Cookie("refresh_token", cookieValue(login, "refresh_token")),
                                new Cookie("XSRF-TOKEN", csrf)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-XSRF-TOKEN", csrf));
    }

    @Test
    @DisplayName("CSRF inválido deve preservar o contrato 403")
    void shouldRejectInvalidCsrf() throws Exception {
        register("csrf-invalido@email.com");
        MvcResult login = login("csrf-invalido@email.com");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contextPath("/api/v1")
                        .cookie(new Cookie("refresh_token", cookieValue(login, "refresh_token")),
                                new Cookie("XSRF-TOKEN", cookieValue(login, "XSRF-TOKEN")))
                        .header("X-XSRF-TOKEN", "valor-incorreto"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN_ERROR"))
                .andExpect(jsonPath("$.message").value("Acesso negado"));
    }

    @Test
    @DisplayName("Falhas de refresh devem ser publicamente equivalentes")
    void shouldReturnEquivalentErrorsForAllRefreshAuthenticationFailures() throws Exception {
        RefreshSession missing = loginSession("erro-ausente@email.com");
        MvcResult missingResponse = refresh(null, missing.csrfToken());

        clearPersistedUsersAndTokens();
        RefreshSession unknown = loginSession("erro-desconhecido@email.com");
        MvcResult unknownResponse = refresh("token-desconhecido", unknown.csrfToken());

        clearPersistedUsersAndTokens();
        RefreshSession expired = loginSession("erro-expirado@email.com");
        RefreshTokenEntity expiredToken = latestToken();
        expiredToken.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        refreshTokenRepository.saveAndFlush(expiredToken);
        MvcResult expiredResponse = refresh(expired.refreshToken(), expired.csrfToken());

        clearPersistedUsersAndTokens();
        RefreshSession revoked = loginSession("erro-revogado@email.com");
        RefreshTokenEntity revokedToken = latestToken();
        revokedToken.setRevokedAt(LocalDateTime.now());
        revokedToken.setRevocationReason(RefreshTokenRevocationReason.LOGOUT);
        refreshTokenRepository.saveAndFlush(revokedToken);
        MvcResult revokedResponse = refresh(revoked.refreshToken(), revoked.csrfToken());

        clearPersistedUsersAndTokens();
        RefreshSession inactive = loginSession("erro-inativo@email.com");
        var inactiveUser = userRepository.findByEmail("erro-inativo@email.com").orElseThrow();
        inactiveUser.setActive(false);
        userRepository.saveAndFlush(inactiveUser);
        MvcResult inactiveResponse = refresh(inactive.refreshToken(), inactive.csrfToken());

        clearPersistedUsersAndTokens();
        RefreshSession insideGrace = loginSession("erro-grace-interna@email.com");
        refreshSuccessfully(insideGrace.refreshToken(), insideGrace.csrfToken());
        MvcResult insideGraceResponse = refresh(insideGrace.refreshToken(), insideGrace.csrfToken());

        clearPersistedUsersAndTokens();
        RefreshSession outsideGrace = loginSession("erro-grace-externa@email.com");
        refreshSuccessfully(outsideGrace.refreshToken(), outsideGrace.csrfToken());
        RefreshTokenEntity predecessor = refreshTokenRepository.findAll().stream()
                .filter(token -> token.getRevocationReason() == RefreshTokenRevocationReason.ROTATED)
                .findFirst()
                .orElseThrow();
        predecessor.setRevokedAt(LocalDateTime.now().minusMinutes(1));
        refreshTokenRepository.saveAndFlush(predecessor);
        MvcResult outsideGraceResponse = refresh(outsideGrace.refreshToken(), outsideGrace.csrfToken());

        PublicRefreshError expected = publicRefreshError(missingResponse);
        List<MvcResult> failures = List.of(
                unknownResponse,
                expiredResponse,
                revokedResponse,
                inactiveResponse,
                insideGraceResponse,
                outsideGraceResponse
        );
        for (MvcResult response : failures) {
            assertEquals(expected, publicRefreshError(response));
        }

        assertRefreshCookieIsCleared(missingResponse);
        assertRefreshCookieIsCleared(unknownResponse);
        assertRefreshCookieIsCleared(expiredResponse);
        assertRefreshCookieIsCleared(revokedResponse);
        assertRefreshCookieIsCleared(inactiveResponse);
        assertFalse(insideGraceResponse.getResponse().getHeaders("Set-Cookie").stream()
                .anyMatch(value -> value.startsWith("refresh_token=")));
        assertRefreshCookieIsCleared(outsideGraceResponse);
    }

    @Test
    @DisplayName("Logout sem refresh token deve continuar idempotente")
    void shouldLogoutWithoutRefreshToken() throws Exception {
        register("logout-sem-token@email.com");
        MvcResult login = login("logout-sem-token@email.com");
        String csrf = login.getResponse().getHeader("X-XSRF-TOKEN");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contextPath("/api/v1")
                        .cookie(new Cookie("XSRF-TOKEN", csrf))
                        .header("X-XSRF-TOKEN", csrf))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Deve carregar os defaults seguros de refresh token")
    void shouldLoadSecureRefreshDefaults() {
        assertEquals(Duration.ofDays(7), refreshTokenProperties.tokenExpiration());
        assertEquals(Duration.ofDays(30), refreshTokenProperties.familyExpiration());
        assertEquals(Duration.ofSeconds(5), refreshTokenProperties.reuseGracePeriod());
        assertTrue(refreshTokenProperties.cookieSecure());
        assertEquals("Strict", refreshTokenProperties.normalizedSameSite());
    }

    @Test
    @DisplayName("Logout deve ser idempotente, revogar a sessão e limpar os cookies")
    void shouldLogoutIdempotently() throws Exception {
        register("logout@email.com");
        MvcResult login = login("logout@email.com");
        String refresh = cookieValue(login, "refresh_token");
        String csrf = login.getResponse().getHeader("X-XSRF-TOKEN");

        for (int attempt = 0; attempt < 2; attempt++) {
            MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout")
                            .contextPath("/api/v1")
                            .cookie(new Cookie("refresh_token", refresh), new Cookie("XSRF-TOKEN", csrf))
                            .header("X-XSRF-TOKEN", csrf))
                    .andExpect(status().isNoContent())
                    .andReturn();
            assertTrue(logout.getResponse().getHeaders("Set-Cookie").stream()
                    .anyMatch(value -> value.startsWith("refresh_token=") && value.contains("Max-Age=0")));
            Cookie clearedCsrf = logout.getResponse().getCookie("XSRF-TOKEN");
            assertNotNull(clearedCsrf);
            assertEquals(0, clearedCsrf.getMaxAge());
        }

        assertEquals(RefreshTokenRevocationReason.LOGOUT,
                refreshTokenRepository.findAll().getFirst().getRevocationReason());
    }

    @Test
    @DisplayName("Usuário inativo deve invalidar e revogar sua família")
    void shouldRejectRefreshForInactiveUser() throws Exception {
        register("inativo-refresh@email.com");
        MvcResult login = login("inativo-refresh@email.com");
        String refresh = cookieValue(login, "refresh_token");
        String csrf = login.getResponse().getHeader("X-XSRF-TOKEN");
        var user = userRepository.findByEmail("inativo-refresh@email.com").orElseThrow();
        user.setActive(false);
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contextPath("/api/v1")
                        .cookie(new Cookie("refresh_token", refresh), new Cookie("XSRF-TOKEN", csrf))
                        .header("X-XSRF-TOKEN", csrf))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Não foi possível renovar a sessão"));

        assertEquals(RefreshTokenRevocationReason.USER_INACTIVE,
                refreshTokenRepository.findAll().getFirst().getRevocationReason());
    }

    @Test
    @DisplayName("Falhas de login devem ser publicamente equivalentes")
    void shouldReturnEquivalentErrorsForAllLoginAuthenticationFailures() throws Exception {
        register("login-ativo@example.test");
        register("login-inativo@example.test");
        var inactiveUser = userRepository.findByEmail("login-inativo@example.test").orElseThrow();
        inactiveUser.setActive(false);
        userRepository.saveAndFlush(inactiveUser);

        MvcResult unknownEmail = loginFailure("login-inexistente@example.test", PASSWORD);
        MvcResult wrongPassword = loginFailure("login-ativo@example.test", "senha-incorreta");
        MvcResult inactive = loginFailure("login-inativo@example.test", PASSWORD);

        PublicLoginError expected = publicLoginError(unknownEmail);
        assertEquals(expected, publicLoginError(wrongPassword));
        assertEquals(expected, publicLoginError(inactive));
    }

    @Test
    void shouldReturnMeDataWhenAuthenticated() throws Exception {
        String registerRequest = """
                {
                  "nome": "Lucas",
                  "email": "lucas@email.com",
                  "senha": "senha-segura"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                .contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest));

        String loginRequest = """
                {
                  "email": "lucas@email.com",
                  "senha": "senha-segura"
                }
                """;

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andReturn().getResponse().getContentAsString();

        String token = com.jayway.jsonpath.JsonPath.read(response, "$.access_token");

        mockMvc.perform(get("/api/v1/auth/me")
                        .contextPath("/api/v1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("lucas@email.com"))
                .andExpect(jsonPath("$.nome").value("Lucas"));
    }

    @Test
    void shouldReturnUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .contextPath("/api/v1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Deve normalizar o e-mail no cadastro e no login")
    void shouldNormalizeEmail() throws Exception {
        String request = """
                {
                  "nome": "Lucas",
                  "email": "  LUCAS@EMAIL.COM  ",
                  "senha": "senha-segura"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register").contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("lucas@email.com"));

        String loginRequest = """
                {
                  "email": " Lucas@Email.Com ",
                  "senha": "senha-segura"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON).content(loginRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists());
    }

    @Test
    @DisplayName("Deve retornar 401 com contrato JSON correto para token ausente")
    void shouldReturnStandardErrorForMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").contextPath("/api/v1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_ERROR"))
                .andExpect(jsonPath("$.message").value("Token inválido ou ausente"));
    }

    @Test
    @DisplayName("Deve retornar 401 quando o sub do token não é um número válido")
    void shouldReturnUnauthorizedForInvalidSub() throws Exception {

        String tokenComSubInvalido = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImlhdCI6MTU3NzgzNjgwMCwiZXhwIjoyNTI0NjA4MDAwLCJhdWQiOlsidGVzdC1hdWRpZW5jZSJdLCJzdWIiOiJhYmMiLCJyb2xlcyI6WyJVU0VSIl19.fake-signature";

        mockMvc.perform(get("/api/v1/auth/me").contextPath("/api/v1")
                        .header("Authorization", "Bearer " + tokenComSubInvalido))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Deve documentar cadastro e login como públicos e /auth/me com Bearer JWT")
    void shouldDocumentAuthenticationRequirementsPerEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/v3/api-docs").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/auth/register'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/auth/login'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/auth/login'].post.description",
                        containsString("cookie XSRF-TOKEN não HttpOnly")))
                .andExpect(jsonPath("$.paths['/auth/refresh'].post").exists())
                .andExpect(jsonPath("$.paths['/auth/logout'].post.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/auth/me'].get.security[0].bearerAuth").isArray());
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Usuário de teste",
                                  "email": "%s",
                                  "senha": "senha-segura"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated());
    }

    private MvcResult login(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "senha": "senha-segura"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private RefreshSession loginSession(String email) throws Exception {
        register(email);
        MvcResult login = login(email);
        return new RefreshSession(
                cookieValue(login, "refresh_token"),
                login.getResponse().getHeader("X-XSRF-TOKEN")
        );
    }

    private MvcResult refresh(String refreshToken, String csrfToken) throws Exception {
        return mockMvc.perform(refreshRequest(refreshToken, csrfToken))
                .andExpect(status().isUnauthorized())
                .andReturn();
    }

    private MvcResult refreshSuccessfully(String refreshToken, String csrfToken) throws Exception {
        return mockMvc.perform(refreshRequest(refreshToken, csrfToken))
                .andExpect(status().isOk())
                .andReturn();
    }

    private MvcResult loginFailure(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "senha": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isUnauthorized())
                .andReturn();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder refreshRequest(
            String refreshToken, String csrfToken) {
        var request = post("/api/v1/auth/refresh")
                .contextPath("/api/v1")
                .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                .header("X-XSRF-TOKEN", csrfToken);
        if (refreshToken != null) {
            request.cookie(new Cookie("refresh_token", refreshToken));
        }
        return request;
    }

    private RefreshTokenEntity latestToken() {
        return refreshTokenRepository.findAll().stream()
                .max(java.util.Comparator.comparing(RefreshTokenEntity::getId))
                .orElseThrow();
    }

    private PublicRefreshError publicRefreshError(MvcResult result) throws Exception {
        assertEquals(401, result.getResponse().getStatus());
        Map<String, Object> body = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$");
        assertEquals(Set.of("timestamp", "status", "error", "message"), body.keySet());
        return new PublicRefreshError(
                result.getResponse().getStatus(),
                ((Number) body.get("status")).intValue(),
                (String) body.get("error"),
                (String) body.get("message"),
                body.keySet()
        );
    }

    private PublicLoginError publicLoginError(MvcResult result) throws Exception {
        assertEquals(401, result.getResponse().getStatus());
        assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(
                MediaType.parseMediaType(result.getResponse().getContentType())));
        Map<String, Object> body = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$");
        assertEquals(Set.of("timestamp", "status", "error", "message"), body.keySet());
        assertEquals(401, ((Number) body.get("status")).intValue());
        assertEquals("UNAUTHORIZED_ERROR", body.get("error"));
        assertEquals("E-mail ou senha inválidos", body.get("message"));
        return new PublicLoginError(
                result.getResponse().getStatus(),
                ((Number) body.get("status")).intValue(),
                (String) body.get("error"),
                (String) body.get("message"),
                body.keySet()
        );
    }

    private void assertRefreshCookieIsCleared(MvcResult result) {
        assertTrue(result.getResponse().getHeaders("Set-Cookie").stream()
                .anyMatch(value -> value.startsWith("refresh_token=") && value.contains("Max-Age=0")));
    }

    private void clearPersistedUsersAndTokens() {
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAll();
    }

    private String cookieValue(MvcResult result, String name) {
        Cookie responseCookie = result.getResponse().getCookie(name);
        if (responseCookie != null) {
            return responseCookie.getValue();
        }
        String header = setCookie(result, name);
        if (header == null) {
            return null;
        }
        int separator = header.indexOf(';');
        String pair = separator >= 0 ? header.substring(0, separator) : header;
        return pair.substring(name.length() + 1);
    }

    private String setCookie(MvcResult result, String name) {
        Collection<String> headers = result.getResponse().getHeaders("Set-Cookie");
        return headers.stream()
                .filter(value -> value.startsWith(name + "="))
                .findFirst()
                .orElse(null);
    }

    private record RefreshSession(String refreshToken, String csrfToken) {
    }

    private record PublicRefreshError(int httpStatus, int status, String error, String message,
                                      Set<String> fields) {
    }

    private record PublicLoginError(int httpStatus, int status, String error, String message, Set<String> fields) {
    }
}
