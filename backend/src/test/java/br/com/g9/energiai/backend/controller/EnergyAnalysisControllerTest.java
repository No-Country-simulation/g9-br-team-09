package br.com.g9.energiai.backend.controller;

import br.com.g9.energiai.backend.client.ml.MlPredictionClient;
import br.com.g9.energiai.backend.client.ml.exception.MlPredictionClientException;
import br.com.g9.energiai.backend.config.JwtProperties;
import br.com.g9.energiai.backend.entity.AppUser;
import br.com.g9.energiai.backend.enums.UserRole;
import br.com.g9.energiai.backend.repository.EnergyAnalysisRepository;
import br.com.g9.energiai.backend.repository.UserRepository;
import br.com.g9.energiai.backend.support.LocalProfileTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@LocalProfileTest
class EnergyAnalysisControllerTest {

    private static final String GENERIC_AUTHENTICATION_MESSAGE = "Token inválido ou usuário não autorizado";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EnergyAnalysisRepository energyAnalysisRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProperties jwtProperties;

    @MockitoBean
    private MlPredictionClient mlPredictionClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        energyAnalysisRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve realizar análise energética com sucesso pela URL pública e retornar resposta completa incluindo ID")
    void shouldPerformAnalysisSuccessfully() throws Exception {
        AppUser currentUser = userRepository.save(AppUser.builder()
                .name("Teste").email("teste@email.com").passwordHash("hash")
                .role(UserRole.USER).active(true).build());

        String token = token(currentUser.getId().toString(), List.of("USER"),
                Instant.now().plusSeconds(900), issuer(), audience(), signingSecret());

        String requestBody = """
            {
              "consumo_kwh": 500,
              "uso_horario_pico": true,
              "quantidade_equipamentos": 10,
              "tipo_imovel": "CASA",
              "horas_alto_consumo": 8
            }
            """;

        when(mlPredictionClient.predict(any())).thenThrow(new MlPredictionClientException("API indisponível"));

        long countBefore = energyAnalysisRepository.count();

        String responseBody = mockMvc.perform(post("/api/v1/analise-energetica")
                        .contextPath("/api/v1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.categoria").value("INEFICIENTE"))
                .andExpect(jsonPath("$.probabilidade").value(0.95))
                .andExpect(jsonPath("$.score").value(95))
                .andExpect(jsonPath("$.custo_estimado_mensal").value(375.00))
                .andExpect(jsonPath("$.fonte_classificacao").value("RULE_BASED_FALLBACK"))
                .andExpect(jsonPath("$.custoEstimadoMensal").doesNotExist())
                .andExpect(jsonPath("$.fonteClassificacao").doesNotExist())
                .andExpect(jsonPath("$.user_id").doesNotExist())
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.recomendacoes").isArray())
                .andExpect(jsonPath("$.recomendacoes.length()").value(4))
                .andExpect(jsonPath("$.recomendacoes", containsInAnyOrder(
                        "Reduzir o uso de equipamentos durante horários de pico.",
                        "Avaliar equipamentos com alto consumo energético.",
                        "Distribuir o consumo ao longo do dia.",
                        "Verificar a eficiência energética dos equipamentos."
                )))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode jsonResponse = objectMapper.readTree(responseBody);
        long persistedId = jsonResponse.get("id").asLong();
        var saved = energyAnalysisRepository.findById(persistedId);

        assertEquals(countBefore + 1, energyAnalysisRepository.count());
        assertTrue(saved.isPresent());
        assertEquals(currentUser.getId(), saved.get().getUser().getId());
        verify(mlPredictionClient).predict(any());
    }

    @Test
    @DisplayName("Deve retornar 401 ao criar análise sem token")
    void shouldReturnUnauthorizedWhenCreatingAnalysisWithoutToken() throws Exception {
        long countBefore = energyAnalysisRepository.count();

        mockMvc.perform(post("/api/v1/analise-energetica")
                        .contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_ERROR"))
                .andExpect(jsonPath("$.message").value("Token inválido ou ausente"));

        assertEquals(countBefore, energyAnalysisRepository.count());
    }

    @Test
    @DisplayName("Deve retornar erro genérico para subject JWT malformado e não persistir")
    void shouldRejectMalformedSubjectWithoutDisclosingDetails() throws Exception {
        String token = token("not-a-number", List.of("USER"), Instant.now().plusSeconds(900),
                issuer(), audience(), signingSecret());

        expectProviderUnauthorized(token);
    }

    @Test
    @DisplayName("Deve retornar erro genérico para usuário inexistente e não persistir")
    void shouldRejectNonexistentUserWithoutDisclosingDetails() throws Exception {
        String token = token("999999", List.of("USER"), Instant.now().plusSeconds(900),
                issuer(), audience(), signingSecret());

        expectProviderUnauthorized(token);
    }

    @Test
    @DisplayName("Deve retornar erro genérico para usuário inativo e não persistir")
    void shouldRejectInactiveUserWithoutDisclosingDetails() throws Exception {
        AppUser inactiveUser = saveUser("Inactive User", "inactive-analysis@example.com", false);
        String token = token(inactiveUser.getId().toString(), List.of("USER"), Instant.now().plusSeconds(900),
                issuer(), audience(), signingSecret());

        expectProviderUnauthorized(token);
    }

    @Test
    @DisplayName("Deve ignorar tentativas do cliente de escolher outro proprietário")
    void shouldUseOnlyJwtSubjectAsOwnershipSource() throws Exception {
        AppUser authenticatedUser = saveUser("User A", "user-a@example.com", true);
        AppUser forgedUser = saveUser("User B", "user-b@example.com", true);
        String token = token(authenticatedUser.getId().toString(), List.of("USER"),
                Instant.now().plusSeconds(900), issuer(), audience(), signingSecret());
        String requestBody = """
                {
                  "consumo_kwh": 500,
                  "uso_horario_pico": true,
                  "quantidade_equipamentos": 10,
                  "tipo_imovel": "CASA",
                  "horas_alto_consumo": 8,
                  "user_id": %d
                }
                """.formatted(forgedUser.getId());

        when(mlPredictionClient.predict(any())).thenThrow(new MlPredictionClientException("API indisponível"));

        String responseBody = mockMvc.perform(post("/api/v1/analise-energetica")
                        .contextPath("/api/v1")
                        .queryParam("user_id", forgedUser.getId().toString())
                        .header("X-User-Id", forgedUser.getId().toString())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").doesNotExist())
                .andExpect(jsonPath("$.user").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long persistedId = objectMapper.readTree(responseBody).get("id").asLong();
        var persistedAnalysis = energyAnalysisRepository.findById(persistedId).orElseThrow();

        assertEquals(authenticatedUser.getId(), persistedAnalysis.getUser().getId());
        assertEquals(1, energyAnalysisRepository.count());
    }

    @Test
    @DisplayName("Deve retornar 400 quando os dados de entrada forem inválidos")
    void shouldReturnBadRequestWhenInputIsInvalid() throws Exception {
        AppUser currentUser = userRepository.save(AppUser.builder()
                .name("Teste").email("teste-invalido@email.com").passwordHash("hash")
                .role(UserRole.USER).active(true).build());

        String token = token(currentUser.getId().toString(), List.of("USER"),
                Instant.now().plusSeconds(900), issuer(), audience(), signingSecret());

        String requestBody = """
            {
              "consumo_kwh": -100,
              "uso_horario_pico": true,
              "quantidade_equipamentos": 10,
              "tipo_imovel": "CASA",
              "horas_alto_consumo": 8
            }
            """;

        mockMvc.perform(post("/api/v1/analise-energetica")
                        .contextPath("/api/v1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message", containsString("consumo_kwh")))
                .andExpect(jsonPath("$.message", not(containsString("consumoKwh"))))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Não deve expor a rota antiga como contrato público")
    void shouldNotServeLegacyRoute() throws Exception {
        AppUser currentUser = userRepository.save(AppUser.builder()
                .name("Teste").email("teste-legado@email.com").passwordHash("hash")
                .role(UserRole.USER).active(true).build());

        String token = token(currentUser.getId().toString(), List.of("USER"),
                Instant.now().plusSeconds(900), issuer(), audience(), signingSecret());

        String requestBody = """
            {
              "consumo_kwh": 500,
              "uso_horario_pico": true,
              "quantidade_equipamentos": 10,
              "tipo_imovel": "CASA",
              "horas_alto_consumo": 8
            }
            """;

        mockMvc.perform(post("/api/v1/analises-energeticas")
                        .contextPath("/api/v1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    private void expectProviderUnauthorized(String token) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/v1/analise-energetica")
                .contextPath("/api/v1")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()));

        result.andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_ERROR"))
                .andExpect(jsonPath("$.message").value(GENERIC_AUTHENTICATION_MESSAGE));

        assertEquals(0, energyAnalysisRepository.count());
        verifyNoInteractions(mlPredictionClient);
    }

    private AppUser saveUser(String name, String email, boolean active) {
        return userRepository.save(AppUser.builder()
                .name(name)
                .email(email)
                .passwordHash("hash")
                .role(UserRole.USER)
                .active(active)
                .build());
    }

    private String validRequestJson() {
        return """
                {
                  "consumo_kwh": 500,
                  "uso_horario_pico": true,
                  "quantidade_equipamentos": 10,
                  "tipo_imovel": "CASA",
                  "horas_alto_consumo": 8
                }
                """;
    }

    private byte[] signingSecret() {
        return Base64.getDecoder().decode(jwtProperties.secret());
    }

    private String issuer() {
        return jwtProperties.issuer();
    }

    private List<String> audience() {
        return List.of(jwtProperties.audience());
    }

    private String token(String subject, List<String> roles, Instant expiresAt, String issuer,
                         List<String> audience, byte[] secret) throws JOSEException {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuer)
                .audience(audience)
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(expiresAt));

        if (roles != null) {
            claims.claim("roles", roles);
        }

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(JOSEObjectType.JWT)
                .build();
        SignedJWT jwt = new SignedJWT(header, claims.build());
        jwt.sign(new MACSigner(secret));
        return jwt.serialize();
    }
}
