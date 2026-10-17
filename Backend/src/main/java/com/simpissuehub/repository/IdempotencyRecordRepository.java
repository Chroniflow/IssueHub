package com.simpissuehub.repository;

import com.simpissuehub.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByUserIdAndOperationAndIdempotencyKey(Long userId, String operation, String idempotencyKey);
}
