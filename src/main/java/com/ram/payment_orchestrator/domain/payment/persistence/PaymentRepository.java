package com.ram.payment_orchestrator.domain.payment.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, String> {

    Optional<PaymentEntity> findByMerchantIdAndIdempotencyKey(String merchantId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.paymentId = :paymentId")
    Optional<PaymentEntity> findByIdForUpdate(@Param("paymentId") String paymentId);

    @Query(value = """
            SELECT p.*
            FROM payments p
            JOIN provider_attempts a
              ON a.payment_id = p.payment_id
             AND a.provider_operation_id = p.provider_operation_id
            WHERE (
                p.status = 'UNKNOWN'
                OR (p.status = 'ROUTED' AND a.outcome IS NULL)
                OR p.status = 'RESOLVING'
            )
              AND p.updated_at <= :staleBefore
              AND (p.resolver_lease_until IS NULL OR p.resolver_lease_until < :now)
              AND p.resolution_attempts < :maxAttempts
            ORDER BY p.updated_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<PaymentEntity> findResolvable(
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore,
            @Param("maxAttempts") int maxAttempts,
            @Param("batchSize") int batchSize
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE payments p
            SET status = 'NEEDS_MANUAL_REVIEW',
                resolver_owner = NULL,
                resolver_lease_until = NULL,
                updated_at = :now
            WHERE p.resolution_attempts >= :maxAttempts
              AND p.updated_at <= :staleBefore
              AND (p.resolver_lease_until IS NULL OR p.resolver_lease_until < :now)
              AND (
                  p.status = 'UNKNOWN'
                  OR p.status = 'RESOLVING'
                  OR (
                      p.status = 'ROUTED'
                      AND EXISTS (
                          SELECT 1 FROM provider_attempts a
                          WHERE a.payment_id = p.payment_id
                            AND a.provider_operation_id = p.provider_operation_id
                            AND a.outcome IS NULL
                      )
                  )
              )
              AND EXISTS (
                  SELECT 1 FROM provider_attempts a
                  WHERE a.payment_id = p.payment_id
                    AND a.provider_operation_id = p.provider_operation_id
              )
            """, nativeQuery = true)
    int moveExhaustedToManualReview(
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore,
            @Param("maxAttempts") int maxAttempts
    );
}
