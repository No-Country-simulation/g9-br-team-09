package br.com.g9.energiai.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] normalizedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .peek(origin -> {
                    if (origin.contains("*")) {
                        throw new IllegalArgumentException(
                                "CORS com credenciais exige origens explícitas, sem wildcard");
                    }
                })
                .toArray(String[]::new);

        registry.addMapping("/**")
                .allowedOrigins(normalizedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-XSRF-TOKEN")
                .exposedHeaders("X-XSRF-TOKEN")
                .allowCredentials(true);
    }
}
