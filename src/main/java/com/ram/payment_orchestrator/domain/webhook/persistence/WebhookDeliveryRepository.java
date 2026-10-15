package com.ram.payment_orchestrator.domain.webhook.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDeliveryEntity, Long> {

    List<WebhookDeliveryEntity> findByPaymentId(String paymentId);

    @Query(value = """
            SELECT * FROM webhook_deliveries
            WHERE status = 'PENDING'
              AND attempts < :maxAttempts
              AND next_attempt_at <= :now
              AND (locked_until IS NULL OR locked_until < :now)
            ORDER BY next_attempt_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<WebhookDeliveryEntity> findReadyToDeliver(
            @Param("now") Instant now,
            @Param("maxAttempts") int maxAttempts,
            @Param("batchSize") int batchSize
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE webhook_deliveries
            SET status = 'DEAD_LETTER', locked_by = NULL, locked_until = NULL
            WHERE status = 'PENDING'
              AND attempts >= :maxAttempts
              AND (locked_until IS NULL OR locked_until < :now)
            """, nativeQuery = true)
    int moveExhaustedToDeadLetter(
            @Param("now") Instant now,
            @Param("maxAttempts") int maxAttempts
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select delivery from WebhookDeliveryEntity delivery where delivery.id = :deliveryId")
    Optional<WebhookDeliveryEntity> findByIdForUpdate(@Param("deliveryId") Long deliveryId);
}
