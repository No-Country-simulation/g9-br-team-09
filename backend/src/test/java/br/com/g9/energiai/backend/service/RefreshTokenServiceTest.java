package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.config.JwtProperties;
import br.com.g9.energiai.backend.config.RefreshTokenProperties;
import br.com.g9.energiai.backend.entity.AppUser;
import br.com.g9.energiai.backend.entity.RefreshTokenEntity;
import br.com.g9.energiai.backend.enums.RefreshTokenRevocationReason;
import br.com.g9.energiai.backend.enums.UserRole;
import br.com.g9.energiai.backend.exception.RefreshTokenAuthenticationException;
import br.com.g9.energiai.backend.mapper.UserMapper;
import br.com.g9.energiai.backend.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T15:00:00Z");
    private static final String RAW_TOKEN = "token-bruto-apresentado";
    private static final String TOKEN_HASH = "a".repeat(64);
    private static final String FAMILY_ID = "family-id";

    @Mock
    private RefreshTokenRepository repository;

    @Mock
    private RefreshTokenGenerator generator;

    @Mock
    private RefreshTokenHasher hasher;

    @Mock
    private JwtTokenService jwtTokenService;

    private RefreshTokenService service;
    private RefreshTokenProperties properties;
    private AppUser user;

    @BeforeEach
    void setUp() {
        properties = new RefreshTokenProperties(
                Duration.ofDays(7), Duration.ofDays(30), Duration.ofSeconds(5),
                "refresh_token", true, "Strict", "/api/v1/auth", ""
        );
        service = new RefreshTokenService(
                repository,
                generator,
                hasher,
                properties,
                jwtTokenService,
                new JwtProperties("unused", "issuer", "audience", Duration.ofMinutes(15)),
                new UserMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        user = AppUser.builder()
                .id(10L)
                .name("Usuário")
                .email("usuario@example.com")
                .passwordHash("unused")
                .role(UserRole.USER)
                .active(true)
                .createdAt(LocalDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC))
                .build();
    }

    @Test
    void shouldCreateNewFamilyPersistingOnlyHash() {
        when(generator.generate()).thenReturn(RAW_TOKEN);
        when(hasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);

        IssuedRefreshToken issued = service.createFamily(user);

        ArgumentCaptor<RefreshTokenEntity> captor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(repository).save(captor.capture());
        RefreshTokenEntity stored = captor.getValue();
        assertEquals(RAW_TOKEN, issued.rawToken());
        assertEquals(Duration.ofDays(7), issued.maxAge());
        assertEquals(TOKEN_HASH, stored.getTokenHash());
        assertNotEquals(RAW_TOKEN, stored.getTokenHash());
        assertNotNull(stored.getFamilyId());
        assertEquals(36, stored.getFamilyId().length());
        assertEquals(now().plusDays(7), stored.getExpiresAt());
        assertEquals(now().plusDays(30), stored.getFamilyExpiresAt());
    }

    @Test
    void shouldRotateTokenPreservingFamilyAndItsAbsoluteExpiration() {
        RefreshTokenEntity predecessor = activeToken();
        prepareFamily(predecessor);
        when(generator.generate()).thenReturn("successor-raw");
        when(hasher.hash("successor-raw")).thenReturn("b".repeat(64));
        when(jwtTokenService.generateToken(user)).thenReturn("access-token");
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            RefreshTokenEntity token = invocation.getArgument(0);
            token.setId(2L);
            return token;
        });

        RefreshResult result = service.refresh(RAW_TOKEN);

        ArgumentCaptor<RefreshTokenEntity> captor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        RefreshTokenEntity successor = captor.getValue();
        assertEquals(FAMILY_ID, successor.getFamilyId());
        assertEquals(predecessor.getFamilyExpiresAt(), successor.getFamilyExpiresAt());
        assertEquals(now().plusDays(7), successor.getExpiresAt());
        assertEquals(RefreshTokenRevocationReason.ROTATED, predecessor.getRevocationReason());
        assertEquals(now(), predecessor.getRevokedAt());
        assertEquals(successor, predecessor.getReplacedByToken());
        assertEquals("access-token", result.response().accessToken());
        assertEquals("successor-raw", result.refreshToken().rawToken());
    }

    @Test
    void shouldLimitSuccessorExpirationToAbsoluteFamilyExpiration() {
        RefreshTokenEntity predecessor = activeToken();
        predecessor.setFamilyExpiresAt(now().plusDays(1));
        prepareFamily(predecessor);
        when(generator.generate()).thenReturn("limited-successor");
        when(hasher.hash("limited-successor")).thenReturn("c".repeat(64));
        when(jwtTokenService.generateToken(user)).thenReturn("access-token");
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshResult result = service.refresh(RAW_TOKEN);

        assertEquals(now().plusDays(1), predecessor.getReplacedByToken().getExpiresAt());
        assertEquals(Duration.ofDays(1), result.refreshToken().maxAge());
    }

    @Test
    void shouldRejectReuseInsideGraceWithoutRevokingFamilyOrClearingCookie() {
        RefreshTokenEntity predecessor = rotatedToken(now().minusSeconds(3));
        RefreshTokenEntity successor = activeSuccessor();
        prepareFamily(predecessor, successor);

        RefreshTokenAuthenticationException exception = assertThrows(
                RefreshTokenAuthenticationException.class,
                () -> service.refresh(RAW_TOKEN)
        );

        assertFalse(exception.shouldClearRefreshCookie());
        assertNull(successor.getRevokedAt());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRevokeActiveFamilyWhenReuseOccursOutsideGrace(CapturedOutput output) {
        RefreshTokenEntity predecessor = rotatedToken(now().minusSeconds(6));
        RefreshTokenEntity successor = activeSuccessor();
        prepareFamily(predecessor, successor);

        RefreshTokenAuthenticationException exception = assertThrows(
                RefreshTokenAuthenticationException.class,
                () -> service.refresh(RAW_TOKEN)
        );

        assertTrue(exception.shouldClearRefreshCookie());
        assertEquals(RefreshTokenRevocationReason.REUSE_DETECTED, successor.getRevocationReason());
        assertEquals(now(), successor.getRevokedAt());
        assertTrue(output.getOut().contains("familyId=" + FAMILY_ID)
                || output.getErr().contains("familyId=" + FAMILY_ID));
        assertFalse(output.getAll().contains(RAW_TOKEN));
        assertFalse(output.getAll().contains(TOKEN_HASH));
        assertFalse(output.getAll().contains("Authorization"));
        assertFalse(output.getAll().contains("Cookie:"));
    }

    @Test
    void shouldGiveInactiveUserPrecedenceOverGracePeriod() {
        user.setActive(false);
        RefreshTokenEntity predecessor = rotatedToken(now().minusSeconds(3));
        RefreshTokenEntity successor = activeSuccessor();
        prepareFamily(predecessor, successor);

        RefreshTokenAuthenticationException exception = assertThrows(
                RefreshTokenAuthenticationException.class,
                () -> service.refresh(RAW_TOKEN)
        );

        assertTrue(exception.shouldClearRefreshCookie());
        assertEquals(RefreshTokenRevocationReason.USER_INACTIVE, successor.getRevocationReason());
    }

    @Test
    void shouldGiveFamilyExpirationPrecedenceOverGracePeriod() {
        RefreshTokenEntity predecessor = rotatedToken(now().minusSeconds(3));
        predecessor.setFamilyExpiresAt(now());
        RefreshTokenEntity successor = activeSuccessor();
        successor.setFamilyExpiresAt(now());
        prepareFamily(predecessor, successor);

        assertThrows(RefreshTokenAuthenticationException.class, () -> service.refresh(RAW_TOKEN));

        assertEquals(RefreshTokenRevocationReason.FAMILY_EXPIRED, successor.getRevocationReason());
    }

    @Test
    void shouldMakeLogoutIdempotentAndRevokeOnlyActivePresentedToken() {
        RefreshTokenEntity token = activeToken();
        prepareFamily(token);

        service.logout(RAW_TOKEN);
        service.logout(RAW_TOKEN);
        service.logout(null);

        assertEquals(RefreshTokenRevocationReason.LOGOUT, token.getRevocationReason());
        assertNotNull(token.getRevokedAt());
    }

    private void prepareFamily(RefreshTokenEntity... tokens) {
        when(hasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(repository.findFamilyIdByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(FAMILY_ID));
        when(repository.findAllByFamilyIdForUpdate(FAMILY_ID)).thenReturn(List.of(tokens));
        when(repository.findAllByFamilyIdOrderById(FAMILY_ID)).thenReturn(List.of(tokens));
    }

    private RefreshTokenEntity activeToken() {
        return token(1L, TOKEN_HASH, now().minusMinutes(1));
    }

    private RefreshTokenEntity activeSuccessor() {
        return token(2L, "b".repeat(64), now().minusSeconds(1));
    }

    private RefreshTokenEntity rotatedToken(LocalDateTime revokedAt) {
        RefreshTokenEntity token = activeToken();
        token.setRevokedAt(revokedAt);
        token.setRevocationReason(RefreshTokenRevocationReason.ROTATED);
        return token;
    }

    private RefreshTokenEntity token(Long id, String hash, LocalDateTime createdAt) {
        return RefreshTokenEntity.builder()
                .id(id)
                .user(user)
                .tokenHash(hash)
                .familyId(FAMILY_ID)
                .createdAt(createdAt)
                .expiresAt(now().plusDays(7))
                .familyExpiresAt(now().plusDays(30))
                .build();
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    }
}
