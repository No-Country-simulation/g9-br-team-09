package br.com.g9.energiai.backend.repository;

import br.com.g9.energiai.backend.entity.AppUser;
import br.com.g9.energiai.backend.enums.UserRole;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:energiai-app-user-oracle;MODE=Oracle;"
        + "DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;"
        + "INIT=CREATE TABLE IF NOT EXISTS ALL_SEQUENCES("
        + "SEQUENCE_NAME VARCHAR2(255), MIN_VALUE NUMBER, MAX_VALUE NUMBER, INCREMENT_BY NUMBER)",
    "spring.flyway.baseline-on-migrate=true",
    "spring.flyway.baseline-version=0",
    "spring.jpa.database-platform=org.hibernate.dialect.OracleDialect",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AppUserRepositoryTest {

    @Autowired
    private UserRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private Environment environment;

    @ParameterizedTest(name = "active={0} deve ser persistido como {1}")
    @CsvSource({"true, 1", "false, 0"})
    void shouldPersistBooleanAsNumericValue(boolean active, int expectedNumericValue) {
        AppUser saved = repository.saveAndFlush(user("persisted-" + active + "@example.com", active));

        Number storedActive = jdbcTemplate.queryForObject(
            "select active from app_user where id = ?",
            Number.class,
            saved.getId()
        );

        assertEquals(expectedNumericValue, storedActive.intValue());
    }

    @ParameterizedTest(name = "ACTIVE={0} deve ser lido como {1}")
    @CsvSource({"1, true", "0, false"})
    void shouldReadNumericValueAsBoolean(int numericValue, boolean expectedActive) {
        jdbcTemplate.update(
            """
            insert into app_user (name, email, password_hash, role, active)
            values (?, ?, ?, ?, ?)
            """,
            "Numeric User",
            "numeric-" + numericValue + "@example.com",
            "unused-password-hash",
            UserRole.USER.name(),
            numericValue
        );
        entityManager.clear();

        AppUser loaded = repository.findByEmail("numeric-" + numericValue + "@example.com").orElseThrow();

        assertEquals(expectedActive, loaded.isActive());
    }

    @Test
    void shouldStartWithFlywayMigrationsAndSchemaValidation() {
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertTrue(
            Arrays.stream(flyway.info().applied())
                .anyMatch(migration -> "2".equals(migration.getVersion().getVersion()))
        );
    }

    private AppUser user(String email, boolean active) {
        return AppUser.builder()
            .name("Persistence Test")
            .email(email)
            .passwordHash("unused-password-hash")
            .role(UserRole.USER)
            .active(active)
            .build();
    }
}
