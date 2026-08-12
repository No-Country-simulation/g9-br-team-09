package br.com.g9.energiai.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OciSecurityConfigurationTest {

    @Test
    void shouldKeepBaseJwtConfigurationExternalAndUseDocumentedDefaults() throws IOException {
        Properties properties = load("application.properties");

        assertEquals("${JWT_SECRET:}", properties.getProperty("jwt.secret"));
        assertEquals("${JWT_ISSUER:energiai-api}", properties.getProperty("jwt.issuer"));
        assertEquals("${JWT_AUDIENCE:energiai-frontend}", properties.getProperty("jwt.audience"));
        assertEquals("${JWT_ACCESS_TOKEN_EXPIRATION:15m}",
                properties.getProperty("jwt.access-token-expiration"));
    }

    @Test
    void shouldKeepOciSecurityConfigurationExternalWithoutStartingOracle() throws IOException {
        Properties properties = load("application.properties");
        properties.putAll(load("application-oci.properties"));

        assertEquals("true", properties.getProperty("auth.refresh.cookie-secure"));
        assertEquals("${AUTH_REFRESH_COOKIE_SAME_SITE:None}",
                properties.getProperty("auth.refresh.cookie-same-site"));
        assertEquals("${CORS_ALLOWED_ORIGINS:https://energiai.vercel.app}",
                properties.getProperty("cors.allowed-origins"));
        assertEquals("framework", properties.getProperty("server.forward-headers-strategy"));

        String allowedOrigins = properties.getProperty("cors.allowed-origins");
        assertTrue(allowedOrigins.startsWith("${CORS_ALLOWED_ORIGINS:"));
        assertTrue(allowedOrigins.endsWith("}"));
        String defaultOrigins = defaultValue(allowedOrigins);
        assertFalse(defaultOrigins.isBlank());
        assertFalse(Arrays.stream(defaultOrigins.split(","))
                .map(String::trim)
                .anyMatch("*"::equals));

        assertEquals("${AUTH_REFRESH_COOKIE_DOMAIN:}",
                properties.getProperty("auth.refresh.cookie-domain"));
        assertEquals("${JWT_SECRET:}", properties.getProperty("jwt.secret"));
        assertEquals("${DB_URL}", properties.getProperty("spring.datasource.url"));
        assertEquals("${DB_USERNAME}", properties.getProperty("spring.datasource.username"));
        assertEquals("${DB_PASSWORD}", properties.getProperty("spring.datasource.password"));
    }

    private Properties load(String resource) throws IOException {
        return PropertiesLoaderUtils.loadProperties(new ClassPathResource(resource));
    }

    private String defaultValue(String placeholder) {
        int separator = placeholder.indexOf(':');
        return placeholder.substring(separator + 1, placeholder.length() - 1);
    }
}
