package br.com.g9.energiai.backend.controller;

import br.com.g9.energiai.backend.config.JwtProperties;
import br.com.g9.energiai.backend.entity.AppUser;
import br.com.g9.energiai.backend.entity.EnergyAnalysisEntity;
import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import br.com.g9.energiai.backend.enums.UserRole;
import br.com.g9.energiai.backend.repository.EnergyAnalysisRepository;
import br.com.g9.energiai.backend.repository.UserRepository;
import br.com.g9.energiai.backend.service.JwtTokenService;
import br.com.g9.energiai.backend.support.LocalProfileTest;
import br.com.g9.energiai.backend.support.TestJwtFactory;
import br.com.g9.energiai.backend.support.TestUserFixtures;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@LocalProfileTest
class EnergyAnalysisGetByIdControllerTest {

    private static final String ANALYSIS_NOT_FOUND_MESSAGE = "Análise não encontrada com o ID informado.";
    private static final String UNAUTHORIZED_MESSAGE= "Token inválido ou ausente";

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

    private TestJwtFactory testJwtFactory;

    private AppUser userA;
    private AppUser userB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setup() {
        energyAnalysisRepository.deleteAll();
        userRepository.deleteAll();

        userA = saveUser("User A", "user-a@example.com", true);
        userB = saveUser("User B", "user-b@example.com", true);
        testJwtFactory = new TestJwtFactory(jwtProperties);
        tokenA = jwtTokenService.generateToken(userA);
        tokenB = jwtTokenService.generateToken(userB);
    }

    @Test
    @DisplayName("Deve retornar 401 ao consultar detalhe sem token")
    void shouldReturnUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/analise-energetica/{id}", 1L).contextPath("/api/v1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_ERROR"))
                .andExpect(jsonPath("$.message").value(UNAUTHORIZED_MESSAGE));
    }

    @Test
    @DisplayName("Deve retornar 401 ao consultar detalhe com token inválido")
    void shouldReturnUnauthorizedWithInvalidToken() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/analise-energetica/{id}", 1L)
                        .contextPath("/api/v1"), "not-a-valid-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_ERROR"))
                .andExpect(jsonPath("$.message").value(UNAUTHORIZED_MESSAGE));
    }

    @Test
    @DisplayName("Deve retornar 401 ao consultar detalhe com token expirado")
    void shouldReturnUnauthorizedWithExpiredToken() throws Exception {
        String expiredToken = testJwtFactory.expiredFor(userA.getId().toString());

        mockMvc.perform(authenticated(get("/api/v1/analise-energetica/{id}", 1L)
                        .contextPath("/api/v1"), expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_ERROR"))
                .andExpect(jsonPath("$.message").value(UNAUTHORIZED_MESSAGE));
    }

    @Test
    @DisplayName("Deve retornar 401 ao consultar detalhe com usuário inexistente no token")
    void shouldReturnUnauthorizedForNonexistentUser() throws Exception {
        String token = jwtTokenService.generateToken(TestUserFixtures.nonPersistedActiveUser(999999L));

        mockMvc.perform(authenticated(get("/api/v1/analise-energetica/{id}", 1L)
                        .contextPath("/api/v1"), token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_ERROR"));
    }

    @Test
    @DisplayName("Deve retornar 401 ao consultar detalhe com usuário inativo")
    void shouldReturnUnauthorizedForInactiveUser() throws Exception {
        AppUser inactiveUser = saveUser("Inactive", "inactive@example.com", false);
        String token = jwtTokenService.generateToken(inactiveUser);

        mockMvc.perform(authenticated(get("/api/v1/analise-energetica/{id}", 1L)
                        .contextPath("/api/v1"), token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_ERROR"));
    }

    @Test
    @DisplayName("Usuário A deve conseguir consultar a própria análise")
    void shouldReturnDetailedAnalysisWhenIdExists() throws Exception {
        EnergyAnalysisEntity saved = persistAnalysis(userA);

        mockMvc.perform(authenticated(get("/api/v1/analise-energetica/{id}", saved.getId())
                        .contextPath("/api/v1"), tokenA))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.consumo_kwh").value(420.0))
                .andExpect(jsonPath("$.uso_horario_pico").value(true))
                .andExpect(jsonPath("$.quantidade_equipamentos").value(10))
                .andExpect(jsonPath("$.tipo_imovel").value("CASA"))
                .andExpect(jsonPath("$.horas_alto_consumo").value(8))
                .andExpect(jsonPath("$.categoria").value("INEFICIENTE"))
                .andExpect(jsonPath("$.probabilidade").value(0.95))
                .andExpect(jsonPath("$.score").value(95))
                .andExpect(jsonPath("$.custo_estimado_mensal").value(315.00))
                .andExpect(jsonPath("$.recomendacoes[0]").value("Dica 1"))
                .andExpect(jsonPath("$.fonte_classificacao").value("RULE_BASED"))
                .andExpect(jsonPath("$.criado_em").exists());
    }

    @Test
    @DisplayName("Deve retornar 404 genérico para ID inexistente")
    void shouldReturn404ForMissingId() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/analise-energetica/{id}", 999L)
                        .contextPath("/api/v1"), tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND_ERROR"))
                .andExpect(jsonPath("$.message").value(ANALYSIS_NOT_FOUND_MESSAGE))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Usuário A não deve conseguir acessar análise de B (404)")
    void shouldReturn404WhenUserAAccessesUserBsAnalysis() throws Exception {
        EnergyAnalysisEntity savedForUserB = persistAnalysis(userB);

        mockMvc.perform(authenticated(get("/api/v1/analise-energetica/{id}", savedForUserB.getId())
                        .contextPath("/api/v1"), tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND_ERROR"))
                .andExpect(jsonPath("$.message").value(ANALYSIS_NOT_FOUND_MESSAGE));
    }

    @Test
    @DisplayName("Usuário B não deve conseguir acessar análise de A (404) - simétrico")
    void shouldReturn404WhenUserBAccessesUserAsAnalysis() throws Exception {
        EnergyAnalysisEntity savedForUserA = persistAnalysis(userA);

        mockMvc.perform(authenticated(get("/api/v1/analise-energetica/{id}", savedForUserA.getId())
                        .contextPath("/api/v1"), tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND_ERROR"))
                .andExpect(jsonPath("$.message").value(ANALYSIS_NOT_FOUND_MESSAGE));
    }

    @Test
    @DisplayName("As respostas para ID inexistente e ID de outro usuário devem ser equivalentes (mesmo status, erro, mensagem e conjunto de campos)")
    void shouldReturnEquivalentResponsesForMissingAndCrossUserAccess() throws Exception {
        EnergyAnalysisEntity savedForUserB = persistAnalysis(userB);

        String responseForMissingId = mockMvc.perform(authenticated(
                        get("/api/v1/analise-energetica/{id}", 999999L).contextPath("/api/v1"), tokenA))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        String responseForOtherUsersAnalysis = mockMvc.perform(authenticated(
                        get("/api/v1/analise-energetica/{id}", savedForUserB.getId()).contextPath("/api/v1"), tokenA))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        Integer statusMissing = JsonPath.read(responseForMissingId, "$.status");
        String errorMissing = JsonPath.read(responseForMissingId, "$.error");
        String messageMissing = JsonPath.read(responseForMissingId, "$.message");

        Integer statusCrossUser = JsonPath.read(responseForOtherUsersAnalysis, "$.status");
        String errorCrossUser = JsonPath.read(responseForOtherUsersAnalysis, "$.error");
        String messageCrossUser = JsonPath.read(responseForOtherUsersAnalysis, "$.message");

        assertEquals(statusMissing, statusCrossUser);
        assertEquals(errorMissing, errorCrossUser);
        assertEquals(messageMissing, messageCrossUser);
    }

    @Test
    @DisplayName("Não deve permitir acesso a registro legado sem proprietário pelo endpoint de detalhe")
    void shouldReturn404ForLegacyRecordWithoutOwner() throws Exception {
        EnergyAnalysisEntity legacy = persistAnalysis(null);

        mockMvc.perform(authenticated(get("/api/v1/analise-energetica/{id}", legacy.getId())
                        .contextPath("/api/v1"), tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(ANALYSIS_NOT_FOUND_MESSAGE));
    }


    private EnergyAnalysisEntity persistAnalysis(AppUser owner) {
        EnergyAnalysisEntity analysis = EnergyAnalysisEntity.builder()
                .user(owner)
                .consumoKwh(420.0)
                .usoHorarioPico(true)
                .quantidadeEquipamentos(10)
                .tipoImovel(PropertyType.CASA)
                .horasAltoConsumo(8)
                .categoria(EnergyCategory.INEFICIENTE)
                .probabilidade(0.95)
                .score(95)
                .custoEstimadoMensal(new BigDecimal("315.00"))
                .fonteClassificacao(ClassificationSource.RULE_BASED)
                .recomendacoes(List.of("Dica 1"))
                .build();

        return energyAnalysisRepository.save(analysis);
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

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }
}
