package br.com.g9.energiai.backend.repository;

import br.com.g9.energiai.backend.entity.EnergyAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EnergyAnalysisRepository extends JpaRepository<EnergyAnalysisEntity, Long> {}
