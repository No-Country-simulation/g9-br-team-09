package br.com.g9.energiai.backend.service;


import br.com.g9.energiai.backend.entity.AppUser;
import br.com.g9.energiai.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SpringSecurityAuthenticatedUserProvider implements AuthenticatedUserProvider {

    private final UserRepository userRepository;

    @Override
    public AppUser getCurrentUser() {
        var principal = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .orElseThrow(() -> new BadCredentialsException("Credencial válida não encontrada"));

        if (!(principal instanceof Jwt jwt))
            throw new BadCredentialsException("Usuário não autenticado");

        var userId = parseUserId(jwt);

        return userRepository.findById(userId)
                .filter(AppUser::isActive)
                .orElseThrow(() -> new BadCredentialsException("Usuário inexistente ou inativo"));
    }

    private Long parseUserId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new BadCredentialsException("Identificador de usuário inválido");
        }
    }
}