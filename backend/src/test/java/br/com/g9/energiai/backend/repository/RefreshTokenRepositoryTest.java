package br.com.g9.energiai.backend.repository;

import br.com.g9.energiai.backend.entity.AppUser;
import br.com.g9.energiai.backend.entity.RefreshTokenEntity;
import br.com.g9.energiai.backend.enums.RefreshTokenRevocationReason;
import br.com.g9.energiai.backend.enums.UserRole;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:energiai-refresh-token-oracle;MODE=Oracle;"
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
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository repository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Flyway flyway;

    @Autowired
    private Environment environment;

    @Test
    void shouldPersistAndReadTokenFamilyAndReplacement() {
        AppUser user = userRepository.saveAndFlush(user());
        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 12, 0);
        RefreshTokenEntity predecessor = repository.saveAndFlush(token(user, "a".repeat(64), "family", now));
        RefreshTokenEntity successor = repository.saveAndFlush(token(user, "b".repeat(64), "family", now.plusSeconds(1)));
        predecessor.setRevokedAt(now.plusSeconds(1));
        predecessor.setRevocationReason(RefreshTokenRevocationReason.ROTATED);
        predecessor.setReplacedByToken(successor);
        repository.flush();
        entityManager.clear();

        List<RefreshTokenEntity> family = repository.findAllByFamilyIdOrderById("family");

        assertEquals(2, family.size());
        assertEquals(successor.getId(), family.getFirst().getReplacedByToken().getId());
        assertEquals(RefreshTokenRevocationReason.ROTATED, family.getFirst().getRevocationReason());
        assertEquals("family", repository.findFamilyIdByTokenHash("a".repeat(64)).orElseThrow());
    }

    @Test
    void shouldEnforceUniqueSuccessorPerPredecessorRelation() {
        AppUser user = userRepository.saveAndFlush(user());
        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 12, 0);
        RefreshTokenEntity successor = repository.saveAndFlush(token(user, "c".repeat(64), "family-unique", now));
        RefreshTokenEntity first = token(user, "d".repeat(64), "family-unique", now);
        first.setReplacedByToken(successor);
        repository.saveAndFlush(first);
        RefreshTokenEntity second = token(user, "e".repeat(64), "family-unique", now);
        second.setReplacedByToken(successor);

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(second));
    }

    @Test
    void shouldDeleteOnlyTokensBelongingToTheRequestedUser() {
        AppUser firstUser = userRepository.saveAndFlush(user());
        AppUser secondUser = userRepository.saveAndFlush(user());
        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 12, 0);
        repository.saveAndFlush(token(firstUser, "f".repeat(64), "first-family", now));
        repository.saveAndFlush(token(firstUser, "g".repeat(64), "first-family", now.plusSeconds(1)));
        repository.saveAndFlush(token(secondUser, "h".repeat(64), "second-family", now));

        assertEquals(2, repository.deleteAllByUserId(firstUser.getId()));
        entityManager.clear();

        List<RefreshTokenEntity> remaining = repository.findAll();
        assertEquals(1, remaining.size());
        assertEquals(secondUser.getId(), remaining.getFirst().getUser().getId());
    }

    @Test
    void shouldStartWithV4MigrationAndOracleSchemaValidation() {
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertTrue(Arrays.stream(flyway.info().applied())
                .anyMatch(migration -> "4".equals(migration.getVersion().getVersion())));
        assertNotNull(entityManager.getMetamodel().entity(RefreshTokenEntity.class));
    }

    private AppUser user() {
        return AppUser.builder()
                .name("Refresh Repository Test")
                .email("refresh-repository-" + System.nanoTime() + "@example.com")
                .passwordHash("unused-password-hash")
                .role(UserRole.USER)
                .active(true)
                .build();
    }

    private RefreshTokenEntity token(AppUser user, String hash, String familyId, LocalDateTime createdAt) {
        return RefreshTokenEntity.builder()
                .user(user)
                .tokenHash(hash)
                .familyId(familyId)
                .createdAt(createdAt)
                .expiresAt(createdAt.plusDays(7))
                .familyExpiresAt(createdAt.plusDays(30))
                .build();
    }
}
