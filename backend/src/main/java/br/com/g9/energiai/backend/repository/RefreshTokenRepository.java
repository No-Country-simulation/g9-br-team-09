package br.com.g9.energiai.backend.repository;

import br.com.g9.energiai.backend.entity.RefreshTokenEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    @Query("SELECT token.familyId FROM RefreshTokenEntity token WHERE token.tokenHash = :tokenHash")
    Optional<String> findFamilyIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT token
            FROM RefreshTokenEntity token
            JOIN FETCH token.user
            WHERE token.familyId = :familyId
            ORDER BY token.id
            """)
    List<RefreshTokenEntity> findAllByFamilyIdForUpdate(@Param("familyId") String familyId);

    List<RefreshTokenEntity> findAllByFamilyIdOrderById(String familyId);

    long countByFamilyId(String familyId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM RefreshTokenEntity token WHERE token.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
