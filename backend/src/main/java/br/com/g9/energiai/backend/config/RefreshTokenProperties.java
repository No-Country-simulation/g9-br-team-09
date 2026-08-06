package br.com.g9.energiai.backend.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "auth.refresh")
public record RefreshTokenProperties(
        @NotNull @DurationUnit(ChronoUnit.SECONDS) Duration tokenExpiration,
        @NotNull @DurationUnit(ChronoUnit.SECONDS) Duration familyExpiration,
        @NotNull @DurationUnit(ChronoUnit.SECONDS) Duration reuseGracePeriod,
        @NotBlank String cookieName,
        boolean cookieSecure,
        @NotBlank String cookieSameSite,
        @NotBlank String cookiePath,
        String cookieDomain
) {

    private static final Set<String> ALLOWED_SAME_SITE = Set.of("strict", "lax", "none");

    @AssertTrue(message = "A validade do refresh token deve ser positiva")
    public boolean isTokenExpirationValid() {
        return tokenExpiration != null && !tokenExpiration.isZero() && !tokenExpiration.isNegative();
    }

    @AssertTrue(message = "A validade da família deve ser igual ou maior que a validade do token")
    public boolean isFamilyExpirationValid() {
        return familyExpiration != null && tokenExpiration != null
                && !familyExpiration.isNegative()
                && !familyExpiration.isZero()
                && familyExpiration.compareTo(tokenExpiration) >= 0;
    }

    @AssertTrue(message = "A janela de tolerância não pode ser negativa")
    public boolean isReuseGracePeriodValid() {
        return reuseGracePeriod != null && !reuseGracePeriod.isNegative();
    }

    @AssertTrue(message = "SameSite deve ser Strict, Lax ou None")
    public boolean isSameSiteValid() {
        return cookieSameSite != null
                && ALLOWED_SAME_SITE.contains(cookieSameSite.toLowerCase(Locale.ROOT));
    }

    @AssertTrue(message = "SameSite=None exige cookie Secure")
    public boolean isNoneSecureCombinationValid() {
        return cookieSameSite == null || !"none".equalsIgnoreCase(cookieSameSite) || cookieSecure;
    }

    @AssertTrue(message = "O path do cookie deve começar com /")
    public boolean isCookiePathValid() {
        return cookiePath != null && cookiePath.startsWith("/");
    }

    public String normalizedSameSite() {
        if (cookieSameSite == null || cookieSameSite.isBlank()) {
            return cookieSameSite;
        }
        String lower = cookieSameSite.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public String normalizedDomain() {
        return cookieDomain == null || cookieDomain.isBlank() ? null : cookieDomain.trim();
    }
}
