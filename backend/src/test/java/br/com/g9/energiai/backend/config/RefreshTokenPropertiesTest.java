package br.com.g9.energiai.backend.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(properties = {
        "AUTH_REFRESH_TOKEN_EXPIRATION=61",
        "AUTH_REFRESH_FAMILY_EXPIRATION=122",
        "AUTH_REFRESH_REUSE_GRACE_PERIOD=3"
})
@ActiveProfiles("test")
class RefreshTokenPropertiesTest {

    @Autowired
    private RefreshTokenProperties properties;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldBindNumericDurationsAsSecondsAndApplyCookieDefaults() {
        assertEquals(Duration.ofSeconds(61), properties.tokenExpiration());
        assertEquals(Duration.ofSeconds(122), properties.familyExpiration());
        assertEquals(Duration.ofSeconds(3), properties.reuseGracePeriod());
        assertEquals("refresh_token", properties.cookieName());
        assertEquals("Strict", properties.normalizedSameSite());
        assertEquals("/api/v1/auth", properties.cookiePath());
    }

    @Test
    void shouldRejectInvalidLifetimeAndInsecureSameSiteNone() {
        RefreshTokenProperties invalid = new RefreshTokenProperties(
                Duration.ZERO,
                Duration.ofSeconds(1),
                Duration.ofSeconds(-1),
                "refresh_token",
                false,
                "None",
                "auth",
                ""
        );

        assertFalse(validator.validate(invalid).isEmpty());
    }
}
