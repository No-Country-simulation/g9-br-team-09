package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.entity.AppUser;
import br.com.g9.energiai.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpringSecurityAuthenticatedUserProviderTest {

    @Mock
    private UserRepository userRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnActiveUserForValidJwtSubject() {
        AppUser user = AppUser.builder().id(42L).active(true).build();
        authenticate(jwtWithSubject("42"));
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        AppUser actual = provider().getCurrentUser();

        assertSame(user, actual);
    }

    @Test
    void shouldRejectMissingAuthentication() {
        assertGenericBadCredentials();
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectUnauthenticatedAuthentication() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertGenericBadCredentials();
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectNullPrincipal() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertGenericBadCredentials();
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectPrincipalThatIsNotJwt() {
        authenticate("not-a-jwt");

        assertGenericBadCredentials();
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectMalformedSubject() {
        authenticate(jwtWithSubject("not-a-number"));

        assertGenericBadCredentials();
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectNullSubject() {
        authenticate(jwtWithoutSubject());

        assertGenericBadCredentials();
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectUserNotFound() {
        authenticate(jwtWithSubject("42"));
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        assertGenericBadCredentials();
    }

    @Test
    void shouldRejectInactiveUser() {
        AppUser user = AppUser.builder().id(42L).active(false).build();
        authenticate(jwtWithSubject("42"));
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        assertGenericBadCredentials();
    }

    private SpringSecurityAuthenticatedUserProvider provider() {
        return new SpringSecurityAuthenticatedUserProvider(userRepository);
    }

    private void authenticate(Object principal) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Jwt jwtWithSubject(String subject) {
        return jwt(Map.of("sub", subject, "roles", List.of("USER")));
    }

    private Jwt jwtWithoutSubject() {
        return jwt(Map.of("roles", List.of("USER")));
    }

    private Jwt jwt(Map<String, Object> claims) {
        Instant now = Instant.now();
        return new Jwt("token", now, now.plusSeconds(900), Map.of("alg", "HS256"), claims);
    }

    private void assertGenericBadCredentials() {
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> provider().getCurrentUser()
        );
        assertEquals(SpringSecurityAuthenticatedUserProvider.GENERIC_AUTHENTICATION_MESSAGE, exception.getMessage());
    }
}
