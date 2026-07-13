package br.com.g9.energiai.backend.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class H2TestProfileConfigurationIntegrationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Deve iniciar o contexto com o profile de teste usando H2 sem console e sem Oracle")
    void shouldStartTestProfileWithH2() throws Exception {
        assertEquals(
            "jdbc:h2:mem:energiai-test;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
            environment.getProperty("spring.datasource.url")
        );
        assertEquals("false", environment.getProperty("spring.h2.console.enabled"));
        assertEquals("true", environment.getProperty("spring.flyway.enabled"));
        assertFalse(environment.getProperty("spring.datasource.url", "").contains("oracle:thin"));
        assertNotNull(flyway);
        assertTrue(
            Arrays.stream(flyway.info().applied())
                .anyMatch(migration -> "1".equals(migration.getVersion().getVersion()))
        );

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            assertEquals("H2", metaData.getDatabaseProductName());
            assertTrue(metaData.getURL().contains("jdbc:h2:mem:energiai-test"));
        }
    }

    @Test
    @DisplayName("Não deve registrar o servlet do H2 Console no profile de teste")
    void shouldNotRegisterH2ConsoleServletInTestProfile() {
        assertFalse(applicationContext.containsBean("h2ConsoleServletRegistration"));
    }
}
