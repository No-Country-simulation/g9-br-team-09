package br.com.g9.energiai.backend.controller;

import br.com.g9.energiai.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void shouldRegisterUserSuccessfully() throws Exception {
        String request = """
                {
                  "nome": "Lucas Rossoni",
                  "email": "lucas@email.com",
                  "senha": "senha-segura"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Lucas Rossoni"))
                .andExpect(jsonPath("$.email").value("lucas@email.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.criado_em").exists());
    }

    @Test
    void shouldReturnConflictWhenEmailAlreadyExists() throws Exception {
        String request = """
                {
                  "nome": "Lucas Rossoni",
                  "email": "lucas@email.com",
                  "senha": "senha-segura"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                .contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT_ERROR"))
                .andExpect(jsonPath("$.message").value("O e-mail informado já está em uso"));
    }

    @Test
    void shouldLoginSuccessfullyAndReturnToken() throws Exception {
        String registerRequest = """
                {
                  "nome": "Lucas",
                  "email": "lucas@email.com",
                  "senha": "senha-segura"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                .contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest));

        String loginRequest = """
                {
                  "email": "lucas@email.com",
                  "senha": "senha-segura"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.usuario.email").value("lucas@email.com"));
    }

    @Test
    void shouldReturnUnauthorizedForInvalidCredentials() throws Exception {
        String loginRequest = """
                {
                  "email": "inexistente@email.com",
                  "senha": "senha"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));
    }

    @Test
    void shouldReturnMeDataWhenAuthenticated() throws Exception {
        String registerRequest = """
                {
                  "nome": "Lucas",
                  "email": "lucas@email.com",
                  "senha": "senha-segura"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                .contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest));

        String loginRequest = """
                {
                  "email": "lucas@email.com",
                  "senha": "senha-segura"
                }
                """;

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andReturn().getResponse().getContentAsString();

        String token = com.jayway.jsonpath.JsonPath.read(response, "$.access_token");

        mockMvc.perform(get("/api/v1/auth/me")
                        .contextPath("/api/v1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("lucas@email.com"))
                .andExpect(jsonPath("$.nome").value("Lucas"));
    }

    @Test
    void shouldReturnUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .contextPath("/api/v1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Deve retornar 401 quando o usuário está inativo")
    void shouldReturnUnauthorizedWhenUserIsInactive() throws Exception {
        String email = "inativo@email.com";
        String registerRequest = """
                {
                  "nome": "Inativo",
                  "email": "%s",
                  "senha": "senha-segura"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/v1/auth/register").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(registerRequest));

        var user = userRepository.findByEmail(email).orElseThrow();
        user.setActive(false);
        userRepository.save(user);

        String loginRequest = """
                {
                  "email": "%s",
                  "senha": "senha-segura"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON).content(loginRequest))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));
    }

    @Test
    @DisplayName("Deve normalizar o e-mail no cadastro e no login")
    void shouldNormalizeEmail() throws Exception {
        String request = """
                {
                  "nome": "Lucas",
                  "email": "  LUCAS@EMAIL.COM  ",
                  "senha": "senha-segura"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register").contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("lucas@email.com"));

        String loginRequest = """
                {
                  "email": " Lucas@Email.Com ",
                  "senha": "senha-segura"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login").contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON).content(loginRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists());
    }

    @Test
    @DisplayName("Deve retornar 401 com contrato JSON correto para token ausente")
    void shouldReturnStandardErrorForMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").contextPath("/api/v1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_ERROR"))
                .andExpect(jsonPath("$.message").value("Token inválido ou ausente"));
    }

    @Test
    @DisplayName("Deve retornar 401 quando o sub do token não é um número válido")
    void shouldReturnUnauthorizedForInvalidSub() throws Exception {

        String tokenComSubInvalido = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImlhdCI6MTU3NzgzNjgwMCwiZXhwIjoyNTI0NjA4MDAwLCJhdWQiOlsidGVzdC1hdWRpZW5jZSJdLCJzdWIiOiJhYmMiLCJyb2xlcyI6WyJVU0VSIl19.fake-signature";

        mockMvc.perform(get("/api/v1/auth/me").contextPath("/api/v1")
                        .header("Authorization", "Bearer " + tokenComSubInvalido))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Deve documentar cadastro e login como públicos e /auth/me com Bearer JWT")
    void shouldDocumentAuthenticationRequirementsPerEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/v3/api-docs").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/auth/register'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/auth/login'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/auth/me'].get.security[0].bearerAuth").isArray());
    }
}
