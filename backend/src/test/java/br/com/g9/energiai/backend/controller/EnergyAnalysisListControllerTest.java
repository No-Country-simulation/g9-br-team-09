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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@LocalProfileTest
class EnergyAnalysisListControllerTest {

    private static final String UNAUTHORIZED_MESSAGE = "Token inválido ou ausente";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EnergyAnalysisRepository energyAnalysisRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    @DisplayName("Deve retornar 401 ao consultar o histórico sem token")
    void shouldReturnUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/analise-energetica").contextPath("/api/v1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_ERROR"))
                .andExpect(jsonPath("$.message").value(UNAUTHORIZED_MESSAGE));
    }

    @Test
    @DisplayName("Deve retornar 401 ao consultar o histórico com token inválido")
    void shouldReturnUnauthorizedWithInvalidToken() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/analise-energetica").contextPath("/api/v1"), "not-a-valid-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_ERROR"))
                .andExpect(jsonPath("$.message").value(UNAUTHORIZED_MESSAGE));
    }

    @Test
    @DisplayName("Deve retornar 401 ao consultar o histórico com token expirado")
    void shouldReturnUnauthorizedWithExpiredToken() throws Exception {
        String expiredToken = testJwtFactory.expiredFor(userA.getId().toString());

        mockMvc.perform(authenticated(get("/api/v1/analise-energetica").contextPath("/api/v1"), expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_ERROR"))
                .andExpect(jsonPath("$.message").value(UNAUTHORIZED_MESSAGE));
    }

    @Test
    @DisplayName("Deve retornar 401 ao consultar o histórico com usuário inexistente no token")
    void shouldReturnUnauthorizedForNonexistentUser() throws Exception {
        String token = jwtTokenService.generateToken(TestUserFixtures.nonPersistedActiveUser(999999L));

        mockMvc.perform(authenticated(get("/api/v1/analise-energetica").contextPath("/api/v1"), token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_ERROR"));
    }

    @Test
    @DisplayName("Deve retornar 401 ao consultar o histórico com usuário inativo")
    void shouldReturnUnauthorizedForInactiveUser() throws Exception {
        AppUser inactiveUser = saveUser("Inactive", "inactive@example.com", false);
        String token = jwtTokenService.generateToken(inactiveUser);

        mockMvc.perform(authenticated(get("/api/v1/analise-energetica").contextPath("/api/v1"), token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_ERROR"));
    }

    @Test
    @DisplayName("Deve retornar histórico vazio paginado com 200 OK para usuário sem análises")
    void shouldReturnEmptyPaginatedHistory() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/analise-energetica").contextPath("/api/v1"), tokenA))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.analises").isArray())
                .andExpect(jsonPath("$.analises").isEmpty())
                .andExpect(jsonPath("$.pagina_atual").value(0))
                .andExpect(jsonPath("$.tamanho_pagina").value(20))
                .andExpect(jsonPath("$.total_elementos").value(0))
                .andExpect(jsonPath("$.total_paginas").value(0));
    }

    @Test
    @DisplayName("Deve ordenar o histórico da análise mais recente para a mais antiga")
    void shouldOrderHistoryByCreatedAtDescending() throws Exception {
        persistAnalysis(userA, EnergyCategory.EFICIENTE, 25, LocalDateTime.of(2026, 7, 12, 18, 30));
        persistAnalysis(userA, EnergyCategory.INEFICIENTE, 95, LocalDateTime.of(2026, 7, 13, 18, 30));

        mockMvc.perform(authenticated(get("/api/v1/analise-energetica").contextPath("/api/v1"), tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analises[0].categoria").value("INEFICIENTE"))
                .andExpect(jsonPath("$.analises[0].score").value(95))
                .andExpect(jsonPath("$.analises[1].categoria").value("EFICIENTE"))
                .andExpect(jsonPath("$.analises[1].score").value(25));
    }

    @Test
    @DisplayName("Deve retornar metadados corretos ao paginar o histórico")
    void shouldPaginateHistory() throws Exception {
        persistAnalysis(userA, EnergyCategory.EFICIENTE, 10, LocalDateTime.of(2026, 7, 9, 18, 30));
        persistAnalysis(userA, EnergyCategory.MODERADO, 20, LocalDateTime.of(2026, 7, 10, 18, 30));
        persistAnalysis(userA, EnergyCategory.MODERADO, 30, LocalDateTime.of(2026, 7, 11, 18, 30));
        persistAnalysis(userA, EnergyCategory.INEFICIENTE, 40, LocalDateTime.of(2026, 7, 12, 18, 30));
        persistAnalysis(userA, EnergyCategory.INEFICIENTE, 50, LocalDateTime.of(2026, 7, 13, 18, 30));

        mockMvc.perform(authenticated(get("/api/v1/analise-energetica")
                        .contextPath("/api/v1")
                        .param("page", "0")
                        .param("size", "2"), tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analises.length()").value(2))
                .andExpect(jsonPath("$.analises[0].score").value(50))
                .andExpect(jsonPath("$.analises[1].score").value(40))
                .andExpect(jsonPath("$.pagina_atual").value(0))
                .andExpect(jsonPath("$.tamanho_pagina").value(2))
                .andExpect(jsonPath("$.total_elementos").value(5))
                .andExpect(jsonPath("$.total_paginas").value(3));

        mockMvc.perform(authenticated(get("/api/v1/analise-energetica")
                        .contextPath("/api/v1")
                        .param("page", "1")
                        .param("size", "2"), tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analises.length()").value(2))
                .andExpect(jsonPath("$.analises[0].score").value(30))
                .andExpect(jsonPath("$.analises[1].score").value(20))
                .andExpect(jsonPath("$.pagina_atual").value(1))
                .andExpect(jsonPath("$.tamanho_pagina").value(2))
                .andExpect(jsonPath("$.total_elementos").value(5))
                .andExpect(jsonPath("$.total_paginas").value(3));
    }

    @Test
    @DisplayName("Usuário A: deve retornar exatamente as próprias 3 análises, sem dados de B nem legado")
    void shouldReturnOnlyUserAsOwnAnalyses() throws Exception {
        persistAnalysis(userA, EnergyCategory.EFICIENTE, 10, LocalDateTime.of(2026, 7, 10, 12, 0));
        persistAnalysis(userA, EnergyCategory.MODERADO, 20, LocalDateTime.of(2026, 7, 11, 12, 0));
        persistAnalysis(userA, EnergyCategory.INEFICIENTE, 30, LocalDateTime.of(2026, 7, 12, 12, 0));

        persistAnalysis(userB, EnergyCategory.EFICIENTE, 40, LocalDateTime.of(2026, 7, 10, 12, 0));
        persistAnalysis(userB, EnergyCategory.INEFICIENTE, 50, LocalDateTime.of(2026, 7, 11, 12, 0));

        persistLegacyAnalysis(EnergyCategory.INEFICIENTE, 99, LocalDateTime.of(2026, 7, 13, 12, 0));

        String response = mockMvc.perform(authenticated(
                        get("/api/v1/analise-energetica").contextPath("/api/v1"), tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analises.length()").value(3))
                .andExpect(jsonPath("$.total_elementos").value(3))
                .andReturn().getResponse().getContentAsString();

        List<Integer> scores = JsonPath.read(response, "$.analises[*].score");
        assertEquals(List.of(30, 20, 10), scores);
    }

    @Test
    @DisplayName("Usuário B: deve retornar exatamente as próprias 2 análises, sem dados de A nem legado")
    void shouldReturnOnlyUserBsOwnAnalyses() throws Exception {
        persistAnalysis(userA, EnergyCategory.EFICIENTE, 10, LocalDateTime.of(2026, 7, 10, 12, 0));
        persistAnalysis(userA, EnergyCategory.MODERADO, 20, LocalDateTime.of(2026, 7, 11, 12, 0));
        persistAnalysis(userA, EnergyCategory.INEFICIENTE, 30, LocalDateTime.of(2026, 7, 12, 12, 0));

        persistAnalysis(userB, EnergyCategory.EFICIENTE, 40, LocalDateTime.of(2026, 7, 10, 12, 0));
        persistAnalysis(userB, EnergyCategory.INEFICIENTE, 50, LocalDateTime.of(2026, 7, 11, 12, 0));

        persistLegacyAnalysis(EnergyCategory.INEFICIENTE, 99, LocalDateTime.of(2026, 7, 13, 12, 0));

        String response = mockMvc.perform(authenticated(
                        get("/api/v1/analise-energetica").contextPath("/api/v1"), tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analises.length()").value(2))
                .andExpect(jsonPath("$.total_elementos").value(2))
                .andReturn().getResponse().getContentAsString();

        List<Integer> scores = JsonPath.read(response, "$.analises[*].score");
        assertEquals(List.of(50, 40), scores);
    }

    @Test
    @DisplayName("Não deve retornar registros legados sem proprietário no histórico pessoal")
    void shouldNotReturnLegacyRecordsWithoutOwner() throws Exception {
        persistAnalysis(userA, EnergyCategory.EFICIENTE, 10, LocalDateTime.of(2026, 7, 10, 12, 0));
        persistLegacyAnalysis(EnergyCategory.INEFICIENTE, 99, LocalDateTime.of(2026, 7, 13, 12, 0));

        mockMvc.perform(authenticated(get("/api/v1/analise-energetica").contextPath("/api/v1"), tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analises.length()").value(1))
                .andExpect(jsonPath("$.total_elementos").value(1))
                .andExpect(jsonPath("$.analises[0].score").value(10));
    }

    private void persistAnalysis(AppUser owner, EnergyCategory categoria, int score, LocalDateTime createdAt) {
        EnergyAnalysisEntity analysis = EnergyAnalysisEntity.builder()
                .user(owner)
                .consumoKwh(420.0)
                .usoHorarioPico(true)
                .quantidadeEquipamentos(10)
                .tipoImovel(PropertyType.CASA)
                .horasAltoConsumo(8)
                .categoria(categoria)
                .probabilidade(0.95)
                .score(score)
                .custoEstimadoMensal(new BigDecimal("315.00"))
                .fonteClassificacao(ClassificationSource.RULE_BASED)
                .recomendacoes(List.of("Dica 1"))
                .build();

        EnergyAnalysisEntity savedAnalysis = energyAnalysisRepository.saveAndFlush(analysis);
        jdbcTemplate.update(
                "UPDATE energy_analysis SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(createdAt),
                savedAnalysis.getId()
        );
    }

    private void persistLegacyAnalysis(EnergyCategory categoria, int score, LocalDateTime createdAt) {
        EnergyAnalysisEntity analysis = EnergyAnalysisEntity.builder()
                .user(null)
                .consumoKwh(420.0)
                .usoHorarioPico(true)
                .quantidadeEquipamentos(10)
                .tipoImovel(PropertyType.CASA)
                .horasAltoConsumo(8)
                .categoria(categoria)
                .probabilidade(0.95)
                .score(score)
                .custoEstimadoMensal(new BigDecimal("315.00"))
                .fonteClassificacao(ClassificationSource.RULE_BASED)
                .recomendacoes(List.of("Dica legado"))
                .build();

        EnergyAnalysisEntity savedAnalysis = energyAnalysisRepository.saveAndFlush(analysis);
        jdbcTemplate.update(
                "UPDATE energy_analysis SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(createdAt),
                savedAnalysis.getId()
        );
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
