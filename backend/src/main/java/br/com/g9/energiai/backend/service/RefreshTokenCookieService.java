package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.config.RefreshTokenProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RefreshTokenCookieService {

    private final RefreshTokenProperties properties;

    public String readRawToken(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, properties.cookieName());
        return cookie == null || cookie.getValue().isBlank() ? null : cookie.getValue();
    }

    public ResponseCookie createRefreshCookie(IssuedRefreshToken token) {
        return cookie(token.rawToken(), token.maxAge());
    }

    public ResponseCookie clearRefreshCookie() {
        return cookie("", Duration.ZERO);
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(properties.cookieName(), value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(properties.normalizedSameSite())
                .path(properties.cookiePath())
                .maxAge(maxAge);

        if (properties.normalizedDomain() != null) {
            builder.domain(properties.normalizedDomain());
        }

        return builder.build();
    }
}
