package com.ram.payment_orchestrator.domain.outbox.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OutboxRepository extends JpaRepository<OutboxEntity, Long> {

    List<OutboxEntity> findByAggregateId(String aggregateId);

    @Query(value = """
            SELECT * FROM outbox
            WHERE status = 'PENDING'
              AND next_attempt_at <= :now
              AND (locked_until IS NULL OR locked_until < :now)
            ORDER BY next_attempt_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEntity> findReadyToPublish(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from OutboxEntity event where event.id = :eventId")
    Optional<OutboxEntity> findByIdForUpdate(@Param("eventId") Long eventId);
}
