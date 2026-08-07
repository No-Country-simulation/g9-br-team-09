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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@LocalProfileTest
class EnergyAnalysisDashboardControllerTest {

    private static final String UNAUTHORIZED_MESSAGE = "Token inválido ou ausente";

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
    @DisplayName("Deve retornar 401 ao consultar o resumo sem token")
    void shouldReturnUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/analise-energetica/resumo").contextPath("/api/v1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_ERROR"))
                .andExpect(jsonPath("$.message").value(UNAUTHORIZED_MESSAGE));
    }

    @Test
    @DisplayName("Deve retornar 401 ao consultar o resumo com token inválido")
    void shouldReturnUnauthorizedWithInvalidToken() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/analise-energetica/resumo").contextPath("/api/v1"), "not-a-valid-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_ERROR"))
                .andExpect(jsonPath("$.message").value(UNAUTHORIZED_MESSAGE));
    }

    @Test
    @DisplayName("Deve retornar 401 ao consultar o resumo com token expirado")
    void shouldReturnUnauthorizedWithExpiredToken() throws Exception {
        String expiredToken = testJwtFactory.expiredFor(userA.getId().toString());

        mockMvc.perform(authenticated(get("/api/v1/analise-energetica/resumo").contextPath("/api/v1"), expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_ERROR"))
                .andExpect(jsonPath("$.message").value(UNAUTHORIZED_MESSAGE));
    }

    @Test
    @DisplayName("Deve retornar 401 ao consultar o resumo com usuário inexistente no token")
    void shouldReturnUnauthorizedForNonexistentUser() throws Exception {
        String token = jwtTokenService.generateToken(TestUserFixtures.nonPersistedActiveUser(999999L));

        mockMvc.perform(authenticated(get("/api/v1/analise-energetica/resumo").contextPath("/api/v1"), token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_ERROR"));
    }

    @Test
    @DisplayName("Deve retornar 401 ao consultar o resumo com usuário inativo")
    void shouldReturnUnauthorizedForInactiveUser() throws Exception {
        AppUser inactiveUser = saveUser("Inactive", "inactive@example.com", false);
        String token = jwtTokenService.generateToken(inactiveUser);

        mockMvc.perform(authenticated(get("/api/v1/analise-energetica/resumo").contextPath("/api/v1"), token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_ERROR"));
    }

    @Test
    @DisplayName("Deve retornar resumo estatístico correto e isolado por usuário autenticado")
    void shouldReturnCorrectDashboardSummary() throws Exception {
        persistAnalysis(userA, EnergyCategory.EFICIENTE, 0.1, 10, new BigDecimal("75.00"), 100.0);
        persistAnalysis(userA, EnergyCategory.INEFICIENTE, 0.9, 90, new BigDecimal("375.00"), 500.0);
        persistAnalysis(userA, EnergyCategory.INEFICIENTE, 0.95, 95, new BigDecimal("450.00"), 600.0);

        persistAnalysis(userB, EnergyCategory.MODERADO, 0.5, 50, new BigDecimal("200.00"), 300.0);

        mockMvc.perform(authenticated(get("/api/v1/analise-energetica/resumo").contextPath("/api/v1"), tokenA))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.total_analises").value(3))
                .andExpect(jsonPath("$.media_consumo_kwh").value(400.0))
                .andExpect(jsonPath("$.media_custo_mensal").value(300.00))
                .andExpect(jsonPath("$.total_eficiente").value(1))
                .andExpect(jsonPath("$.total_moderado").value(0))
                .andExpect(jsonPath("$.total_ineficiente").value(2));

        mockMvc.perform(authenticated(get("/api/v1/analise-energetica/resumo").contextPath("/api/v1"), tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_analises").value(1))
                .andExpect(jsonPath("$.media_consumo_kwh").value(300.0))
                .andExpect(jsonPath("$.media_custo_mensal").value(200.00))
                .andExpect(jsonPath("$.total_eficiente").value(0))
                .andExpect(jsonPath("$.total_moderado").value(1))
                .andExpect(jsonPath("$.total_ineficiente").value(0));
    }

    @Test
    @DisplayName("Deve retornar valores zerados quando usuário autenticado não possuir análises")
    void shouldReturnZerosWhenNoDataExists() throws Exception {
        persistAnalysis(userB, EnergyCategory.INEFICIENTE, 0.9, 90, new BigDecimal("375.00"), 500.0);

        mockMvc.perform(authenticated(get("/api/v1/analise-energetica/resumo").contextPath("/api/v1"), tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_analises").value(0))
                .andExpect(jsonPath("$.media_consumo_kwh").value(0.0))
                .andExpect(jsonPath("$.media_custo_mensal").value(0.0))
                .andExpect(jsonPath("$.total_eficiente").value(0))
                .andExpect(jsonPath("$.total_moderado").value(0))
                .andExpect(jsonPath("$.total_ineficiente").value(0));
    }

    @Test
    @DisplayName("Não deve incluir registros legados sem proprietário no resumo")
    void shouldExcludeLegacyRecordsFromSummary() throws Exception {
        persistAnalysis(userA, EnergyCategory.EFICIENTE, 0.1, 10, new BigDecimal("75.00"), 100.0);
        persistAnalysis(null, EnergyCategory.INEFICIENTE, 0.9, 90, new BigDecimal("999.00"), 999.0);

        mockMvc.perform(authenticated(get("/api/v1/analise-energetica/resumo").contextPath("/api/v1"), tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_analises").value(1))
                .andExpect(jsonPath("$.media_consumo_kwh").value(100.0))
                .andExpect(jsonPath("$.media_custo_mensal").value(75.00))
                .andExpect(jsonPath("$.total_eficiente").value(1))
                .andExpect(jsonPath("$.total_ineficiente").value(0));
    }

    private void persistAnalysis(AppUser owner, EnergyCategory categoria, double probabilidade, int score,
                                 BigDecimal custo, double consumoKwh) {
        EnergyAnalysisEntity analysis = EnergyAnalysisEntity.builder()
                .user(owner)
                .consumoKwh(consumoKwh)
                .usoHorarioPico(true)
                .quantidadeEquipamentos(2)
                .tipoImovel(PropertyType.CASA)
                .horasAltoConsumo(4)
                .categoria(categoria)
                .probabilidade(probabilidade)
                .score(score)
                .custoEstimadoMensal(custo)
                .fonteClassificacao(ClassificationSource.RULE_BASED)
                .recomendacoes(List.of())
                .build();

        energyAnalysisRepository.save(analysis);
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
