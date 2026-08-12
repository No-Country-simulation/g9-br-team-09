package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.config.RefreshTokenConfig;
import br.com.g9.energiai.backend.config.RefreshTokenProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTokenCookieServiceTest {

    @Test
    void shouldEmitAndClearSecureRefreshCookieWithConfiguredDomain() {
        RefreshTokenProperties properties = properties(true, "None", "api.example.test");
        RefreshTokenCookieService service = new RefreshTokenCookieService(properties);

        String created = service.createRefreshCookie(new IssuedRefreshToken("opaque-value", Duration.ofSeconds(90)))
                .toString();
        String cleared = service.clearRefreshCookie().toString();

        assertRefreshCookieAttributes(created, "refresh_session=opaque-value", true, "None", "api.example.test", 90);
        assertRefreshCookieAttributes(cleared, "refresh_session=", true, "None", "api.example.test", 0);
    }

    @Test
    void shouldEmitLocalRefreshCookieWithoutSecureOrDomain() {
        RefreshTokenProperties properties = properties(false, "Strict", "");
        RefreshTokenCookieService service = new RefreshTokenCookieService(properties);

        String created = service.createRefreshCookie(new IssuedRefreshToken("opaque-value", Duration.ofSeconds(60)))
                .toString();
        String cleared = service.clearRefreshCookie().toString();

        assertRefreshCookieAttributes(created, "refresh_session=opaque-value", false, "Strict", null, 60);
        assertRefreshCookieAttributes(cleared, "refresh_session=", false, "Strict", null, 0);
    }

    @Test
    void shouldEmitAndClearNonHttpOnlyCsrfCookieWithConfiguredAttributes() {
        RefreshTokenProperties properties = properties(true, "None", "api.example.test");
        CsrfTokenRepository repository = new RefreshTokenConfig().csrfTokenRepository(properties);
        MockServletContext servletContext = new MockServletContext();
        servletContext.setMajorVersion(5);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfToken token = repository.generateToken(request);

        repository.saveToken(token, request, response);
        repository.saveToken(null, request, response);

        String created = response.getHeaders("Set-Cookie").getFirst();
        String cleared = response.getHeaders("Set-Cookie").getLast();
        assertCsrfCookieAttributes(created, true, "None", "api.example.test", false);
        assertCsrfCookieAttributes(cleared, true, "None", "api.example.test", true);
    }

    @Test
    void shouldEmitLocalNonHttpOnlyCsrfCookieWithoutSecureOrDomain() {
        RefreshTokenProperties properties = properties(false, "Strict", "");
        CsrfTokenRepository repository = new RefreshTokenConfig().csrfTokenRepository(properties);
        MockServletContext servletContext = new MockServletContext();
        servletContext.setMajorVersion(5);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveToken(repository.generateToken(request), request, response);

        assertCsrfCookieAttributes(response.getHeader("Set-Cookie"), false, "Strict", null, false);
    }

    private RefreshTokenProperties properties(boolean secure, String sameSite, String domain) {
        return new RefreshTokenProperties(
                Duration.ofDays(7),
                Duration.ofDays(30),
                Duration.ofSeconds(5),
                "refresh_session",
                secure,
                sameSite,
                "/api/v1/auth",
                domain
        );
    }

    private void assertRefreshCookieAttributes(String cookie, String nameAndValue, boolean secure, String sameSite,
                                               String domain, long maxAge) {
        assertTrue(cookie.startsWith(nameAndValue));
        assertTrue(cookie.contains("HttpOnly"));
        assertCookieAttributes(cookie, secure, sameSite, domain, maxAge);
    }

    private void assertCsrfCookieAttributes(String cookie, boolean secure, String sameSite, String domain,
                                            boolean cleared) {
        assertTrue(cookie.startsWith("XSRF-TOKEN="));
        assertFalse(cookie.contains("HttpOnly"));
        assertCookieAttributes(cookie, secure, sameSite, domain, cleared ? 0 : -1);
    }

    private void assertCookieAttributes(String cookie, boolean secure, String sameSite, String domain, long maxAge) {
        assertTrue(cookie.contains("Path=/api/v1/auth"));
        assertTrue(cookie.contains("SameSite=" + sameSite),
                () -> "Cookie sem SameSite esperado: " + redactCookieValue(cookie));
        assertEqualsCookieAttribute(cookie, "Secure", secure);
        assertEqualsCookieAttribute(cookie, "Domain=" + domain, domain != null);
        if (maxAge >= 0) {
            assertTrue(cookie.contains("Max-Age=" + maxAge));
        } else {
            assertFalse(cookie.contains("Max-Age="));
        }
    }

    private void assertEqualsCookieAttribute(String cookie, String attribute, boolean expected) {
        if (expected) {
            assertTrue(cookie.contains(attribute));
        } else {
            assertFalse(cookie.contains(attribute));
        }
    }

    private String redactCookieValue(String cookie) {
        return cookie.replaceFirst("^[^=]+=([^;]*)", "XSRF-TOKEN=[REDACTED]");
    }
}
