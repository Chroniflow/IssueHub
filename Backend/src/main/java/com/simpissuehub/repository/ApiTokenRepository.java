package com.simpissuehub.repository;
import com.simpissuehub.domain.ApiToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {
    List<ApiToken> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
    Optional<ApiToken> findByTokenHash(String tokenHash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from ApiToken token where token.id = :id")
    Optional<ApiToken> findByIdForUpdate(@Param("id") Long id);
}
