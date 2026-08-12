package br.com.g9.energiai.backend.repository;

import br.com.g9.energiai.backend.entity.EnergyAnalysisEntity;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface EnergyAnalysisRepository extends JpaRepository<EnergyAnalysisEntity, Long> {

    Page<EnergyAnalysisEntity> findAllByUserIdOrderByCreatedAtDesc(long userId, Pageable pageable);

    Optional<EnergyAnalysisEntity> findByIdAndUserId(Long analysisId, long userId);

    long countByUserId(long userId);

    long countByUserIdAndCategoria(Long userId, EnergyCategory categoria);

    @Query("SELECT AVG(e.consumoKwh) FROM EnergyAnalysisEntity e WHERE e.user.id = :userId")
    Double getAverageConsumoKwhByUserId(@Param("userId") long userId);

    @Query("SELECT SUM(e.custoEstimadoMensal) FROM EnergyAnalysisEntity e WHERE e.user.id = :userId")
    BigDecimal getTotalMonthlyCostByUserId(@Param("userId") long userId);
}
