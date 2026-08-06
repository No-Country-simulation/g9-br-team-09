package br.com.g9.energiai.backend.controller;

import br.com.g9.energiai.backend.dto.request.UserLoginRequest;
import br.com.g9.energiai.backend.dto.request.UserRegistrationRequest;
import br.com.g9.energiai.backend.dto.response.AuthenticatedUserResponse;
import br.com.g9.energiai.backend.dto.response.ApiErrorResponse;
import br.com.g9.energiai.backend.dto.response.AuthenticationResponse;
import br.com.g9.energiai.backend.dto.response.UserRegistrationResponse;
import br.com.g9.energiai.backend.service.AuthenticationService;
import br.com.g9.energiai.backend.service.CsrfCookieService;
import br.com.g9.energiai.backend.service.LoginResult;
import br.com.g9.energiai.backend.service.RefreshResult;
import br.com.g9.energiai.backend.service.RefreshTokenCookieService;
import br.com.g9.energiai.backend.service.RefreshTokenService;
import br.com.g9.energiai.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
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
@Tag(name = "Autenticação", description = "Operações de cadastro, login e renovação de sessão")
public class AuthController {

    private final UserService userService;
    private final AuthenticationService authenticationService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCookieService refreshTokenCookieService;
    private final CsrfCookieService csrfCookieService;

    @PostMapping("/register")
    @Operation(summary = "Cadastrar novo usuário")
    public ResponseEntity<UserRegistrationResponse> register(@RequestBody @Valid UserRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Autenticar usuário",
            description = "Retorna o access token no JSON; emite o refresh token apenas no cookie HttpOnly e o "
                    + "cookie XSRF-TOKEN não HttpOnly. O cliente deve ler preferencialmente o token CSRF no header "
                    + "X-XSRF-TOKEN exposto por CORS; o refresh token nunca aparece no JSON."
    )
    public ResponseEntity<AuthenticationResponse> login(@RequestBody @Valid UserLoginRequest request) {
        LoginResult result = authenticationService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshTokenCookieService.createRefreshCookie(result.refreshToken()).toString())
                .body(result.response());
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Renovar sessão com rotação do refresh token",
            description = "Não recebe body. Rotaciona o refresh token do cookie e mantém o contrato JSON do login.",
            parameters = {
                    @Parameter(name = "refresh_token", in = ParameterIn.COOKIE, required = true,
                            description = "Refresh token opaco emitido no login ou refresh anterior"),
                    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
                            description = "Valor correspondente ao cookie XSRF-TOKEN")
            }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessão renovada e refresh token rotacionado",
                    content = @Content(schema = @Schema(implementation = AuthenticationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh token ausente, inválido ou indisponível",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Token CSRF ausente ou inválido",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<AuthenticationResponse> refresh(HttpServletRequest request) {
        String rawToken = refreshTokenCookieService.readRawToken(request);
        RefreshResult result = refreshTokenService.refresh(rawToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshTokenCookieService.createRefreshCookie(result.refreshToken()).toString())
                .body(result.response());
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Encerrar sessão atual",
            description = "Não recebe body. Revoga de forma idempotente a sessão apresentada e remove os cookies.",
            parameters = {
                    @Parameter(name = "refresh_token", in = ParameterIn.COOKIE,
                            description = "Refresh token opaco, quando presente"),
                    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
                            description = "Valor correspondente ao cookie XSRF-TOKEN")
            }
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sessão encerrada ou já ausente"),
            @ApiResponse(responseCode = "403", description = "Token CSRF ausente ou inválido",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        refreshTokenService.logout(refreshTokenCookieService.readRawToken(request));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieService.clearRefreshCookie().toString());
        csrfCookieService.clear(request, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Obter dados do usuário autenticado", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<AuthenticatedUserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        try {
            Long userId = Long.valueOf(jwt.getSubject());
            return ResponseEntity.ok(authenticationService.getMe(userId));
        } catch (NumberFormatException ignored) {
            throw new BadCredentialsException("Token com identificador inválido");
        }
    }
}
