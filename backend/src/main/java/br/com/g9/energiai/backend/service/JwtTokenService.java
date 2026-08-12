package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.config.JwtProperties;
import br.com.g9.energiai.backend.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public String generateToken(AppUser user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(jwtProperties.accessTokenExpiration());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .audience(List.of(jwtProperties.audience()))
                .claim("roles", List.of(user.getRole().name()))
                .claim("jti", UUID.randomUUID().toString())
                .build();

        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        var parameters = JwtEncoderParameters.from(header, claims);

        return jwtEncoder.encode(parameters).getTokenValue();
    }
}
