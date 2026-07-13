package br.com.g9.energiai.backend.repository;

import br.com.g9.energiai.backend.entity.EnergyAnalysisEntity;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface EnergyAnalysisRepository extends JpaRepository<EnergyAnalysisEntity, Long> {

    Long countByCategoria(EnergyCategory categoria);

    @Query("SELECT AVG(e.consumoKwh) FROM EnergyAnalysisEntity e")
    Double getAverageConsumoKwh();

    @Query("SELECT SUM(e.custoEstimadoMensal) FROM EnergyAnalysisEntity e")
    BigDecimal getTotalCustoMensal();
}
