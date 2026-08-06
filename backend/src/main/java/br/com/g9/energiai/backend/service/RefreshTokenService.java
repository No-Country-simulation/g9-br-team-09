package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.config.JwtProperties;
import br.com.g9.energiai.backend.config.RefreshTokenProperties;
import br.com.g9.energiai.backend.dto.response.AuthenticationResponse;
import br.com.g9.energiai.backend.entity.AppUser;
import br.com.g9.energiai.backend.entity.RefreshTokenEntity;
import br.com.g9.energiai.backend.enums.RefreshTokenRevocationReason;
import br.com.g9.energiai.backend.exception.RefreshTokenAuthenticationException;
import br.com.g9.energiai.backend.mapper.UserMapper;
import br.com.g9.energiai.backend.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenProperties refreshTokenProperties;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final UserMapper userMapper;
    private final Clock clock;

    @Transactional
    public IssuedRefreshToken createFamily(AppUser user) {
        LocalDateTime now = now();
        LocalDateTime familyExpiresAt = now.plus(refreshTokenProperties.familyExpiration());
        LocalDateTime expiresAt = min(now.plus(refreshTokenProperties.tokenExpiration()), familyExpiresAt);
        String rawToken = refreshTokenGenerator.generate();

        refreshTokenRepository.save(RefreshTokenEntity.builder()
                .user(user)
                .tokenHash(refreshTokenHasher.hash(rawToken))
                .familyId(UUID.randomUUID().toString())
                .expiresAt(expiresAt)
                .familyExpiresAt(familyExpiresAt)
                .createdAt(now)
                .build());

        return new IssuedRefreshToken(rawToken, Duration.between(now, expiresAt));
    }

    @Transactional(noRollbackFor = RefreshTokenAuthenticationException.class)
    public RefreshResult refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw authenticationFailure(true);
        }

        String tokenHash = refreshTokenHasher.hash(rawToken);
        String familyId = refreshTokenRepository.findFamilyIdByTokenHash(tokenHash)
                .orElseThrow(() -> authenticationFailure(true));

        refreshTokenRepository.findAllByFamilyIdForUpdate(familyId);
        List<RefreshTokenEntity> family = refreshTokenRepository.findAllByFamilyIdOrderById(familyId);
        RefreshTokenEntity presentedToken = family.stream()
                .filter(token -> tokenHash.equals(token.getTokenHash()))
                .findFirst()
                .orElseThrow(() -> authenticationFailure(true));

        LocalDateTime now = now();
        AppUser user = presentedToken.getUser();

        if (!user.isActive()) {
            revokeActiveFamily(family, now, RefreshTokenRevocationReason.USER_INACTIVE);
            throw authenticationFailure(true);
        }

        if (!now.isBefore(presentedToken.getFamilyExpiresAt())) {
            revokeActiveFamily(family, now, RefreshTokenRevocationReason.FAMILY_EXPIRED);
            throw authenticationFailure(true);
        }

        if (family.stream().anyMatch(token -> token.getRevocationReason()
                == RefreshTokenRevocationReason.REUSE_DETECTED)) {
            throw authenticationFailure(true);
        }

        if (presentedToken.getRevokedAt() != null) {
            handleRevokedToken(family, presentedToken, now);
        }

        if (!now.isBefore(presentedToken.getExpiresAt())) {
            throw authenticationFailure(true);
        }

        String successorRawToken = refreshTokenGenerator.generate();
        LocalDateTime successorExpiresAt = min(
                now.plus(refreshTokenProperties.tokenExpiration()),
                presentedToken.getFamilyExpiresAt()
        );

        RefreshTokenEntity successor = refreshTokenRepository.saveAndFlush(RefreshTokenEntity.builder()
                .user(user)
                .tokenHash(refreshTokenHasher.hash(successorRawToken))
                .familyId(presentedToken.getFamilyId())
                .expiresAt(successorExpiresAt)
                .familyExpiresAt(presentedToken.getFamilyExpiresAt())
                .createdAt(now)
                .build());

        presentedToken.setRevokedAt(now);
        presentedToken.setRevocationReason(RefreshTokenRevocationReason.ROTATED);
        presentedToken.setReplacedByToken(successor);

        AuthenticationResponse response = new AuthenticationResponse(
                jwtTokenService.generateToken(user),
                "Bearer",
                jwtProperties.accessTokenExpiration().toSeconds(),
                userMapper.toAuthenticatedUserResponse(user)
        );

        return new RefreshResult(
                response,
                new IssuedRefreshToken(successorRawToken, Duration.between(now, successorExpiresAt))
        );
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        String tokenHash = refreshTokenHasher.hash(rawToken);
        refreshTokenRepository.findFamilyIdByTokenHash(tokenHash).ifPresent(familyId -> {
            refreshTokenRepository.findAllByFamilyIdForUpdate(familyId);
            List<RefreshTokenEntity> family = refreshTokenRepository.findAllByFamilyIdOrderById(familyId);
            family.stream()
                    .filter(token -> tokenHash.equals(token.getTokenHash()))
                    .filter(token -> token.getRevokedAt() == null)
                    .findFirst()
                    .ifPresent(token -> {
                        token.setRevokedAt(now());
                        token.setRevocationReason(RefreshTokenRevocationReason.LOGOUT);
                    });
        });
    }

    private void handleRevokedToken(List<RefreshTokenEntity> family, RefreshTokenEntity token, LocalDateTime now) {
        if (token.getRevocationReason() != RefreshTokenRevocationReason.ROTATED) {
            throw authenticationFailure(true);
        }

        LocalDateTime reuseDeadline = token.getRevokedAt().plus(refreshTokenProperties.reuseGracePeriod());
        if (!now.isAfter(reuseDeadline)) {
            throw authenticationFailure(false);
        }

        revokeActiveFamily(family, now, RefreshTokenRevocationReason.REUSE_DETECTED);
        log.warn("Reutilização de refresh token detectada: tokenId={}, userId={}, familyId={}",
                token.getId(), token.getUser().getId(), token.getFamilyId());
        throw authenticationFailure(true);
    }

    private void revokeActiveFamily(List<RefreshTokenEntity> family, LocalDateTime now,
                                    RefreshTokenRevocationReason reason) {
        family.stream()
                .filter(token -> token.getRevokedAt() == null)
                .forEach(token -> {
                    token.setRevokedAt(now);
                    token.setRevocationReason(reason);
                });
    }

    private RefreshTokenAuthenticationException authenticationFailure(boolean clearRefreshCookie) {
        return new RefreshTokenAuthenticationException(clearRefreshCookie);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private LocalDateTime min(LocalDateTime first, LocalDateTime second) {
        return first.isBefore(second) ? first : second;
    }
}
