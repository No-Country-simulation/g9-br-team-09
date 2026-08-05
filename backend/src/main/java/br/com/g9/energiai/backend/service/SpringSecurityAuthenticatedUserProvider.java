package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.entity.AppUser;
import br.com.g9.energiai.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpringSecurityAuthenticatedUserProvider implements AuthenticatedUserProvider {

    static final String GENERIC_AUTHENTICATION_MESSAGE = "Token inválido ou usuário não autorizado";

    private final UserRepository userRepository;

    @Override
    public AppUser getCurrentUser() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        Authentication authentication = securityContext == null ? null : securityContext.getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw invalidCredentials();
        }

        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw invalidCredentials();
        }

        Long userId = parseUserId(jwt);

        return userRepository.findById(userId)
                .filter(AppUser::isActive)
                .orElseThrow(this::invalidCredentials);
    }

    private Long parseUserId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw invalidCredentials();
        }
    }

    private BadCredentialsException invalidCredentials() {
        return new BadCredentialsException(GENERIC_AUTHENTICATION_MESSAGE);
    }
}
