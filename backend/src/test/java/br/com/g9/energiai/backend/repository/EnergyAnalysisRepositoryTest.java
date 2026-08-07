package br.com.g9.energiai.backend.repository;

import br.com.g9.energiai.backend.entity.AppUser;
import br.com.g9.energiai.backend.entity.EnergyAnalysisEntity;
import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import br.com.g9.energiai.backend.enums.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class EnergyAnalysisRepositoryTest {

    @Autowired
    private EnergyAnalysisRepository energyAnalysisRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private AppUser userA;
    private AppUser userB;

    @BeforeEach
    void setup() {
        userA = userRepository.save(user("User A", uniqueEmail("user-a")));
        userB = userRepository.save(user("User B", uniqueEmail("user-b")));
    }

    @Test
    @DisplayName("Deve persistir e recuperar a análise usando apenas a tabela energy_analysis")
    void shouldPersistAndLoadAnalysisUsingSingleTable() {
        List<String> recommendations = List.of(
            "Reduzir o uso de equipamentos durante horários de pico.",
            "Avaliar equipamentos com alto consumo energético."
        );

        EnergyAnalysisEntity entity = EnergyAnalysisEntity.builder()
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
            .recomendacoes(recommendations)
            .build();

        EnergyAnalysisEntity saved = energyAnalysisRepository.saveAndFlush(entity);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());

        String rawRecommendations = jdbcTemplate.queryForObject(
            "select recomendacoes from energy_analysis where id = ?",
            String.class,
            saved.getId()
        );
        Integer domainTableCount = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where upper(table_name) = 'ENERGY_ANALYSIS'",
            Integer.class
        );
        Integer recommendationsTableCount = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where upper(table_name) = 'ENERGY_ANALYSIS_RECOMMENDATIONS'",
            Integer.class
        );

        entityManager.clear();

        EnergyAnalysisEntity reloaded = energyAnalysisRepository.findById(saved.getId()).orElseThrow();

        assertEquals(1, domainTableCount);
        assertEquals(0, recommendationsTableCount);
        assertEquals("[\"Reduzir o uso de equipamentos durante horários de pico.\",\"Avaliar equipamentos com alto consumo energético.\"]", rawRecommendations);
        assertEquals(PropertyType.CASA, reloaded.getTipoImovel());
        assertEquals(EnergyCategory.INEFICIENTE, reloaded.getCategoria());
        assertEquals(ClassificationSource.RULE_BASED, reloaded.getFonteClassificacao());
        assertEquals(new BigDecimal("315.00"), reloaded.getCustoEstimadoMensal());
        assertEquals(recommendations, reloaded.getRecomendacoes());
        assertEquals(saved.getId(), reloaded.getId());
        assertTrue(reloaded.getUsoHorarioPico());
        assertNotNull(reloaded.getCreatedAt());
        assertTrue(reloaded.getCreatedAt().isEqual(saved.getCreatedAt()) || reloaded.getCreatedAt().isAfter(saved.getCreatedAt()));
    }

    @Test
    @DisplayName("findAllByUserIdOrderByCreatedAtDesc deve retornar somente registros do proprietário informado")
    void findAllByUserId_shouldReturnOnlyOwnedRecords() {
        persist(userA, EnergyCategory.EFICIENTE, 10);
        persist(userA, EnergyCategory.MODERADO, 20);
        persist(userB, EnergyCategory.INEFICIENTE, 30);
        persistLegacy(EnergyCategory.INEFICIENTE, 40);

        Pageable pageable = PageRequest.of(0, 10);
        Page<EnergyAnalysisEntity> pageA = energyAnalysisRepository
                .findAllByUserIdOrderByCreatedAtDesc(userA.getId(), pageable);

        assertEquals(2, pageA.getTotalElements());
        assertTrue(pageA.getContent().stream().allMatch(e -> e.getUser().getId().equals(userA.getId())));
    }

    @Test
    @DisplayName("findByIdAndUserId deve retornar vazio quando o ID pertencer a outro usuário")
    void findByIdAndUserId_shouldReturnEmptyForOtherUsersRecord() {
        EnergyAnalysisEntity analysisOfB = persist(userB, EnergyCategory.INEFICIENTE, 50);

        Optional<EnergyAnalysisEntity> asA = energyAnalysisRepository
                .findByIdAndUserId(analysisOfB.getId(), userA.getId());
        Optional<EnergyAnalysisEntity> asB = energyAnalysisRepository
                .findByIdAndUserId(analysisOfB.getId(), userB.getId());

        assertTrue(asA.isEmpty());
        assertTrue(asB.isPresent());
    }

    @Test
    @DisplayName("findByIdAndUserId deve retornar vazio para registro legado sem proprietário")
    void findByIdAndUserId_shouldReturnEmptyForLegacyRecord() {
        EnergyAnalysisEntity legacy = persistLegacy(EnergyCategory.MODERADO, 60);

        Optional<EnergyAnalysisEntity> result = energyAnalysisRepository
                .findByIdAndUserId(legacy.getId(), userA.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Agregações (count, média, custo) devem ser filtradas por user_id no banco")
    void aggregations_shouldBeFilteredByUserId() {
        persist(userA, EnergyCategory.EFICIENTE, 10);
        persist(userA, EnergyCategory.INEFICIENTE, 90);
        persist(userB, EnergyCategory.MODERADO, 50);
        persistLegacy(EnergyCategory.INEFICIENTE, 999);

        assertEquals(2L, energyAnalysisRepository.countByUserId(userA.getId()));
        assertEquals(1L, energyAnalysisRepository.countByUserId(userB.getId()));

        assertEquals(1L, energyAnalysisRepository.countByUserIdAndCategoria(userA.getId(), EnergyCategory.EFICIENTE));
        assertEquals(1L, energyAnalysisRepository.countByUserIdAndCategoria(userA.getId(), EnergyCategory.INEFICIENTE));
        assertEquals(0L, energyAnalysisRepository.countByUserIdAndCategoria(userB.getId(), EnergyCategory.EFICIENTE));

        assertEquals(50.0, energyAnalysisRepository.getAverageConsumoKwhByUserId(userA.getId()));
        // Cada registro persistido via helper "persist" usa custoEstimadoMensal fixo de 300.00,
        // logo os 2 registros do usuário A somam 600.00.
        assertEquals(new BigDecimal("600.00"), energyAnalysisRepository.getTotalMonthlyCostByUserId(userA.getId()));
    }

    @Test
    @DisplayName("Usuário sem análises deve receber agregações nulas/zero, sem erro")
    void aggregations_shouldHandleUserWithoutAnalyses() {
        assertEquals(0L, energyAnalysisRepository.countByUserId(userA.getId()));
        assertNull(energyAnalysisRepository.getAverageConsumoKwhByUserId(userA.getId()));
        assertNull(energyAnalysisRepository.getTotalMonthlyCostByUserId(userA.getId()));
    }
    private EnergyAnalysisEntity persist(AppUser owner, EnergyCategory categoria, double consumoKwh) {
        EnergyAnalysisEntity analysis = EnergyAnalysisEntity.builder()
                .user(owner)
                .consumoKwh(consumoKwh)
                .usoHorarioPico(true)
                .quantidadeEquipamentos(2)
                .tipoImovel(PropertyType.CASA)
                .horasAltoConsumo(4)
                .categoria(categoria)
                .probabilidade(0.5)
                .score(50)
                .custoEstimadoMensal(new BigDecimal("300.00"))
                .fonteClassificacao(ClassificationSource.RULE_BASED)
                .recomendacoes(List.of())
                .build();
        return energyAnalysisRepository.saveAndFlush(analysis);
    }

    private EnergyAnalysisEntity persistLegacy(EnergyCategory categoria, double consumoKwh) {
        return persist(null, categoria, consumoKwh);
    }

    private AppUser user(String name, String email) {
        return AppUser.builder()
                .name(name)
                .email(email)
                .passwordHash("hash")
                .role(UserRole.USER)
                .active(true)
                .build();
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
