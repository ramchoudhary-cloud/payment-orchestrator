package com.ram.payment_orchestrator.domain.payment.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FailoverWorkRepository extends JpaRepository<FailoverWorkEntity, Long> {

    @Query(value = """
            SELECT * FROM payment_failover_work
            WHERE status = 'PENDING'
               OR (status = 'CLAIMED' AND lease_until < :now)
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<FailoverWorkEntity> findClaimable(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from FailoverWorkEntity w where w.id = :id")
    Optional<FailoverWorkEntity> findByIdForUpdate(@Param("id") Long id);
}
