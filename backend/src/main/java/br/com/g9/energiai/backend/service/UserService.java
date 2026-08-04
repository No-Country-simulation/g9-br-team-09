package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.dto.request.UserRegistrationRequest;
import br.com.g9.energiai.backend.dto.response.UserRegistrationResponse;
import br.com.g9.energiai.backend.entity.AppUser;
import br.com.g9.energiai.backend.enums.UserRole;
import br.com.g9.energiai.backend.exception.ResourceNotFoundException;
import br.com.g9.energiai.backend.mapper.UserMapper;
import br.com.g9.energiai.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    @Transactional
    public UserRegistrationResponse register(UserRegistrationRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new UserAlreadyExistsException("O e-mail informado já está em uso");
        }

        AppUser user = AppUser.builder()
                .name(request.nome())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.USER)
                .active(true)
                .build();

        AppUser savedUser = userRepository.save(user);
        return userMapper.toRegistrationResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public AppUser findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    @Transactional(readOnly = true)
    public AppUser findByEmail(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    public String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase();
    }
}
