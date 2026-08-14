package br.com.g9.energiai.backend.controller;

import br.com.g9.energiai.backend.client.ml.MlPredictionClient;
import br.com.g9.energiai.backend.client.ml.dto.MlPredictionResponse;
import br.com.g9.energiai.backend.client.ml.exception.MlPredictionClientException;
import br.com.g9.energiai.backend.config.JwtProperties;
import br.com.g9.energiai.backend.entity.AppUser;
import br.com.g9.energiai.backend.enums.UserRole;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.repository.EnergyAnalysisRepository;
import br.com.g9.energiai.backend.repository.UserRepository;
import br.com.g9.energiai.backend.service.JwtTokenService;
import br.com.g9.energiai.backend.support.LocalProfileTest;
import br.com.g9.energiai.backend.support.TestJwtFactory;
import br.com.g9.energiai.backend.support.TestUserFixtures;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.stream.Stream;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private MlPredictionClient mlPredictionClient;

    @Autowired
    private ObjectMapper objectMapper;

    private TestJwtFactory testJwtFactory;

    @BeforeEach
    void setup() {
        energyAnalysisRepository.deleteAll();
        userRepository.deleteAll();
        testJwtFactory = new TestJwtFactory(jwtProperties);
    }

    @Test
    @DisplayName("Deve realizar análise energética com sucesso pela URL pública e retornar resposta completa incluindo ID")
    void shouldPerformAnalysisSuccessfully() throws Exception {
        double mlProbability = 0.8848920863309353;
        AppUser currentUser = userRepository.save(AppUser.builder()
                .name("Teste").email("teste@email.com").passwordHash("hash")
                .role(UserRole.USER).active(true).build());

        String token = jwtTokenService.generateToken(currentUser);

        String requestBody = """
            {
              "consumo_kwh": 500,
              "uso_horario_pico": true,
              "quantidade_equipamentos": 10,
              "tipo_imovel": "CASA",
              "horas_alto_consumo": 8
            }
            """;

        when(mlPredictionClient.predict(any())).thenReturn(new MlPredictionResponse(
                EnergyCategory.MODERADO, mlProbability, 81, List.of("Recomendação do modelo"), "v1"
        ));

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
                .andExpect(jsonPath("$.categoria").value("MODERADO"))
                .andExpect(jsonPath("$.probabilidade").value(mlProbability))
                .andExpect(jsonPath("$.score").value(81))
                .andExpect(jsonPath("$.custo_estimado_mensal").value(375.00))
                .andExpect(jsonPath("$.fonte_classificacao").value("ML_MODEL"))
                .andExpect(jsonPath("$.custoEstimadoMensal").doesNotExist())
                .andExpect(jsonPath("$.fonteClassificacao").doesNotExist())
                .andExpect(jsonPath("$.user_id").doesNotExist())
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.recomendacoes").isArray())
                .andExpect(jsonPath("$.recomendacoes.length()").value(1))
                .andExpect(jsonPath("$.recomendacoes", containsInAnyOrder("Recomendação do modelo")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode jsonResponse = objectMapper.readTree(responseBody);
        long persistedId = jsonResponse.get("id").asLong();
        var saved = energyAnalysisRepository.findById(persistedId);

        String detailBody = mockMvc.perform(get("/api/v1/analise-energetica/{id}", persistedId)
                        .contextPath("/api/v1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode detailResponse = objectMapper.readTree(detailBody);

        assertEquals(countBefore + 1, energyAnalysisRepository.count());
        assertTrue(saved.isPresent());
        assertEquals(currentUser.getId(), saved.get().getUser().getId());
        assertEquals(jsonResponse.get("categoria"), detailResponse.get("categoria"));
        assertEquals(jsonResponse.get("probabilidade"), detailResponse.get("probabilidade"));
        assertEquals(jsonResponse.get("score"), detailResponse.get("score"));
        assertEquals(jsonResponse.get("custo_estimado_mensal"), detailResponse.get("custo_estimado_mensal"));
        assertEquals(jsonResponse.get("fonte_classificacao"), detailResponse.get("fonte_classificacao"));
        verify(mlPredictionClient).predict(any());
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "Deve persistir uma única análise com fallback para {0}")
    @org.junit.jupiter.params.provider.MethodSource("mlFailures")
    void shouldPersistOneFallbackAnalysisForMlFailure(String ignoredDescription,
                                                       MlPredictionClientException failure) throws Exception {
        AppUser currentUser = saveUser("Fallback User", "fallback-" + ignoredDescription.hashCode() + "@example.com", true);
        when(mlPredictionClient.predict(any())).thenThrow(failure);

        long countBefore = energyAnalysisRepository.count();

        mockMvc.perform(post("/api/v1/analise-energetica")
                        .contextPath("/api/v1")
                        .header("Authorization", "Bearer " + jwtTokenService.generateToken(currentUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fonte_classificacao").value("RULE_BASED_FALLBACK"));

        assertEquals(countBefore + 1, energyAnalysisRepository.count());
        verify(mlPredictionClient).predict(any());
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "Deve persistir uma única análise com fallback para resposta inválida: {0}")
    @org.junit.jupiter.params.provider.MethodSource("invalidMlPredictions")
    void shouldPersistOneFallbackAnalysisForInvalidMlResponse(String ignoredDescription,
                                                               MlPredictionResponse prediction) throws Exception {
        AppUser currentUser = saveUser("Invalid ML User", "invalid-" + ignoredDescription.hashCode() + "@example.com", true);
        when(mlPredictionClient.predict(any())).thenReturn(prediction);

        long countBefore = energyAnalysisRepository.count();

        mockMvc.perform(post("/api/v1/analise-energetica")
                        .contextPath("/api/v1")
                        .header("Authorization", "Bearer " + jwtTokenService.generateToken(currentUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fonte_classificacao").value("RULE_BASED_FALLBACK"));

        assertEquals(countBefore + 1, energyAnalysisRepository.count());
        verify(mlPredictionClient).predict(any());
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> mlFailures() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("timeout de conexão", new MlPredictionClientException("Falha", new SocketTimeoutException("Connect timed out"))),
                org.junit.jupiter.params.provider.Arguments.of("timeout de leitura", new MlPredictionClientException("Falha", new SocketTimeoutException("Read timed out"))),
                org.junit.jupiter.params.provider.Arguments.of("conexão recusada", new MlPredictionClientException("Falha", new IOException("Connection refused"))),
                org.junit.jupiter.params.provider.Arguments.of("erro HTTP", new MlPredictionClientException("Falha HTTP")),
                org.junit.jupiter.params.provider.Arguments.of("body vazio", new MlPredictionClientException("A API de ML retornou uma resposta sem corpo"))
        );
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> invalidMlPredictions() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("resposta nula", null),
                org.junit.jupiter.params.provider.Arguments.of("categoria nula", new MlPredictionResponse(null, 0.5, 50, List.of("Dica"), null)),
                org.junit.jupiter.params.provider.Arguments.of("recomendação em branco", new MlPredictionResponse(EnergyCategory.EFICIENTE, 0.5, 50, List.of(" "), null))
        );
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
        String token = testJwtFactory.withSubject("not-a-number");

        expectProviderUnauthorized(token);
    }

    @Test
    @DisplayName("Deve retornar erro genérico para usuário inexistente e não persistir")
    void shouldRejectNonexistentUserWithoutDisclosingDetails() throws Exception {
        String token = jwtTokenService.generateToken(TestUserFixtures.nonPersistedActiveUser(999999L));

        expectProviderUnauthorized(token);
    }

    @Test
    @DisplayName("Deve retornar erro genérico para usuário inativo e não persistir")
    void shouldRejectInactiveUserWithoutDisclosingDetails() throws Exception {
        AppUser inactiveUser = saveUser("Inactive User", "inactive-analysis@example.com", false);
        String token = jwtTokenService.generateToken(inactiveUser);

        expectProviderUnauthorized(token);
    }

    @Test
    @DisplayName("Deve ignorar tentativas do cliente de escolher outro proprietário")
    void shouldUseOnlyJwtSubjectAsOwnershipSource() throws Exception {
        AppUser authenticatedUser = saveUser("User A", "user-a@example.com", true);
        AppUser forgedUser = saveUser("User B", "user-b@example.com", true);
        String token = jwtTokenService.generateToken(authenticatedUser);
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

        String token = jwtTokenService.generateToken(currentUser);

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

        String token = jwtTokenService.generateToken(currentUser);

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

}
