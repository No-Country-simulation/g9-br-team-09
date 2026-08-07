package br.com.g9.energiai.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(RefreshTokenProperties.class)
public class RefreshTokenConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository(RefreshTokenProperties properties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName("XSRF-TOKEN");
        repository.setHeaderName("X-XSRF-TOKEN");
        repository.setCookiePath(properties.cookiePath());
        repository.setCookieCustomizer(builder -> {
            builder.httpOnly(false)
                    .secure(properties.cookieSecure())
                    .sameSite(properties.normalizedSameSite())
                    .path(properties.cookiePath());
            if (properties.normalizedDomain() != null) {
                builder.domain(properties.normalizedDomain());
            }
        });
        return repository;
    }
}
