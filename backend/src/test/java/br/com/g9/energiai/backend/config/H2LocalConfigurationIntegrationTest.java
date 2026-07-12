package br.com.g9.energiai.backend.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
class H2LocalConfigurationIntegrationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Autowired
    private ServletRegistrationBean<?> h2ConsoleServletRegistration;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Deve iniciar o contexto com o profile local sem depender de Oracle")
    void shouldStartContextWithLocalProfile() {
        assertArrayEquals(new String[]{"local"}, environment.getActiveProfiles());
        assertEquals(
            "jdbc:h2:mem:energiai;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
            environment.getProperty("spring.datasource.url")
        );
        assertEquals("org.h2.Driver", environment.getProperty("spring.datasource.driver-class-name"));
        assertEquals("true", environment.getProperty("spring.flyway.enabled"));
        assertFalse(environment.getProperty("spring.datasource.url", "").contains("oracle:thin"));
    }

    @Test
    @DisplayName("Deve disponibilizar um DataSource H2 em memória para o ambiente local")
    void shouldProvideH2DataSource() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            assertEquals("H2", metaData.getDatabaseProductName());
            assertEquals("H2 JDBC Driver", metaData.getDriverName());
            assertTrue(metaData.getURL().contains("jdbc:h2:mem:energiai"));
        }
    }

    @Test
    @DisplayName("Deve inicializar o Flyway apontando para classpath db migration sem exigir migrations de domínio")
    void shouldInitializeFlywayWithoutDomainMigrations() throws Exception {
        assertNotNull(flyway);
        assertEquals("classpath:db/migration", environment.getProperty("spring.flyway.locations"));
        assertEquals("true", environment.getProperty("spring.flyway.validate-on-migrate"));
        assertEquals("true", environment.getProperty("spring.flyway.clean-disabled"));

        try (Connection connection = dataSource.getConnection();
             ResultSet resultSet = connection.getMetaData().getTables(null, null, "ENERGY_ANALYSIS", null)) {
            assertFalse(resultSet.next());
        }
    }

    @Test
    @DisplayName("Deve registrar o servlet do H2 Console apenas no profile local")
    void shouldRegisterH2ConsoleServletInLocalProfile() {
        assertNotNull(h2ConsoleServletRegistration);
        assertEquals(Set.of("/h2-console/*"), h2ConsoleServletRegistration.getUrlMappings());
        assertEquals(Map.of("webAllowOthers", "false"), h2ConsoleServletRegistration.getInitParameters());
        assertEquals(
            1L,
            applicationContext.getBeansOfType(ServletRegistrationBean.class)
                .values()
                .stream()
                .filter(registration -> registration.getUrlMappings().contains("/h2-console/*"))
                .count()
        );
    }
}
