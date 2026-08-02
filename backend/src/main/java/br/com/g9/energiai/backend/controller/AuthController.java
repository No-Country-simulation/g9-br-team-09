package br.com.g9.energiai.backend.controller;

import br.com.g9.energiai.backend.dto.request.UserLoginRequest;
import br.com.g9.energiai.backend.dto.request.UserRegistrationRequest;
import br.com.g9.energiai.backend.dto.response.AuthenticatedUserResponse;
import br.com.g9.energiai.backend.dto.response.AuthenticationResponse;
import br.com.g9.energiai.backend.dto.response.UserRegistrationResponse;
import br.com.g9.energiai.backend.service.AuthenticationService;
import br.com.g9.energiai.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Operações de cadastro e login")
public class AuthController {

    private final UserService userService;
    private final AuthenticationService authenticationService;

    @PostMapping("/register")
    @Operation(summary = "Cadastrar novo usuário")
    public ResponseEntity<UserRegistrationResponse> register(@RequestBody @Valid UserRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Autenticar usuário")
    public ResponseEntity<AuthenticationResponse> login(@RequestBody @Valid UserLoginRequest request) {
        return ResponseEntity.ok(authenticationService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Obter dados do usuário autenticado", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<AuthenticatedUserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        return ResponseEntity.ok(authenticationService.getMe(userId));
    }
}
