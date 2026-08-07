package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.entity.AppUser;
import br.com.g9.energiai.backend.entity.RefreshTokenEntity;
import br.com.g9.energiai.backend.enums.RefreshTokenRevocationReason;
import br.com.g9.energiai.backend.enums.UserRole;
import br.com.g9.energiai.backend.exception.RefreshTokenAuthenticationException;
import br.com.g9.energiai.backend.repository.RefreshTokenRepository;
import br.com.g9.energiai.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "auth.refresh.reuse-grace-period=0")
@ActiveProfiles("test")
class RefreshTokenConcurrencyIntegrationTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void shouldSerializeConcurrentRotationAndReuseWithRealTransactions() throws Exception {
        AppUser user = userRepository.saveAndFlush(AppUser.builder()
                .name("Concorrência")
                .email("concorrencia@example.com")
                .passwordHash("unused")
                .role(UserRole.USER)
                .active(true)
                .build());
        String rawToken = refreshTokenService.createFamily(user).rawToken();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Boolean> first = executor.submit(() -> refreshAfterBarrier(rawToken, ready, start));
        Future<Boolean> second = executor.submit(() -> refreshAfterBarrier(rawToken, ready, start));

        assertTrue(ready.await(2, TimeUnit.SECONDS));
        start.countDown();
        List<Boolean> outcomes = List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
        );

        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, outcomes.stream().filter(success -> !success).count());
        var family = refreshTokenRepository.findAll();
        assertEquals(2, family.size());
        assertEquals(1, family.stream()
                .filter(token -> token.getRevocationReason() == RefreshTokenRevocationReason.ROTATED)
                .count());
        assertEquals(1, family.stream()
                .filter(token -> token.getRevocationReason() == RefreshTokenRevocationReason.REUSE_DETECTED)
                .count());
        assertEquals(0, family.stream().filter(token -> token.getRevokedAt() == null).count());
    }

    @Test
    void shouldRevokeFamilyWhenSuccessorRotationRacesWithPredecessorReuse() throws Exception {
        AppUser user = userRepository.saveAndFlush(AppUser.builder()
                .name("Concorrência de sucessor")
                .email("concorrencia-sucessor@example.com")
                .passwordHash("unused")
                .role(UserRole.USER)
                .active(true)
                .build());
        IssuedRefreshToken predecessor = refreshTokenService.createFamily(user);
        RefreshResult firstRotation = refreshTokenService.refresh(predecessor.rawToken());
        RefreshTokenEntity rotatedPredecessor = refreshTokenRepository.findAll().stream()
                .filter(token -> token.getRevocationReason() == RefreshTokenRevocationReason.ROTATED)
                .findFirst()
                .orElseThrow();
        rotatedPredecessor.setRevokedAt(LocalDateTime.now().minusMinutes(1));
        refreshTokenRepository.saveAndFlush(rotatedPredecessor);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Boolean> successorRotation = executor.submit(
                () -> refreshAfterBarrier(firstRotation.refreshToken().rawToken(), ready, start));
        Future<Boolean> predecessorReuse = executor.submit(
                () -> refreshAfterBarrier(predecessor.rawToken(), ready, start));

        assertTrue(ready.await(2, TimeUnit.SECONDS));
        start.countDown();
        assertFalse(predecessorReuse.get(10, TimeUnit.SECONDS));
        successorRotation.get(10, TimeUnit.SECONDS);

        List<RefreshTokenEntity> family = refreshTokenRepository.findAll();
        assertEquals(0, family.stream().filter(token -> token.getRevokedAt() == null).count());
        assertTrue(family.stream().anyMatch(token -> token.getRevocationReason()
                == RefreshTokenRevocationReason.REUSE_DETECTED));
        assertTrue(family.size() <= 3);
        assertThrows(RefreshTokenAuthenticationException.class,
                () -> refreshTokenService.refresh(firstRotation.refreshToken().rawToken()));
    }

    private boolean refreshAfterBarrier(String rawToken, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(2, TimeUnit.SECONDS)) {
            throw new IllegalStateException("As duas transações não chegaram à barreira");
        }
        try {
            refreshTokenService.refresh(rawToken);
            return true;
        } catch (RefreshTokenAuthenticationException exception) {
            return false;
        }
    }
}
