package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.config.JwtProperties;
import br.com.g9.energiai.backend.dto.request.UserLoginRequest;
import br.com.g9.energiai.backend.dto.response.AuthenticatedUserResponse;
import br.com.g9.energiai.backend.dto.response.AuthenticationResponse;
import br.com.g9.energiai.backend.entity.AppUser;
import br.com.g9.energiai.backend.mapper.UserMapper;
import br.com.g9.energiai.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final UserMapper userMapper;
    private final JwtProperties jwtProperties;

    @Transactional(readOnly = true)
    public AuthenticationResponse login(UserLoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        AppUser user = userRepository.findByEmail(normalizedEmail)
                .filter(AppUser::getActive)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("E-mail ou senha inválidos."));

        String token = jwtTokenService.generateToken(user);

        return new AuthenticationResponse(
                token,
                "Bearer",
                jwtProperties.accessTokenExpiration().toSeconds(),
                userMapper.toAuthenticatedUserResponse(user)
        );
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserResponse getMe(Long userId) {
        AppUser user = userRepository.findById(userId)
                .filter(AppUser::getActive)
                .orElseThrow(() -> new BadCredentialsException("Usuário inexistente ou inativo."));

        return userMapper.toAuthenticatedUserResponse(user);
    }
}
