package br.com.g9.energiai.backend.config;

import jakarta.servlet.Servlet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local")
@ConditionalOnProperty(prefix = "spring.h2.console", name = "enabled", havingValue = "true")
public class H2ConsoleConfig {

    @Bean
    ServletRegistrationBean<Servlet> h2ConsoleServletRegistration(
            @Value("${spring.h2.console.path:/h2-console}") String consolePath,
            @Value("${spring.h2.console.settings.web-allow-others:false}") boolean webAllowOthers
    ) {
        ServletRegistrationBean<Servlet> registrationBean =
            new ServletRegistrationBean<>(createH2ConsoleServlet(), consolePath + "/*");

        registrationBean.addInitParameter("webAllowOthers", Boolean.toString(webAllowOthers));
        registrationBean.setLoadOnStartup(1);

        return registrationBean;
    }

    private Servlet createH2ConsoleServlet() {
        try {
            return (Servlet) Class
                .forName("org.h2.server.web.JakartaWebServlet")
                .getDeclaredConstructor()
                .newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Não foi possível inicializar o servlet do H2 Console", exception);
        }
    }
}
