package com.ram.payment_orchestrator.domain.idempotency.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyKeyEntity, Long> {

    Optional<IdempotencyKeyEntity> findByMerchantIdAndIdempotencyKey(String merchantId, String idempotencyKey);

    /**
     * Tries to reclaim an existing IN_PROGRESS key if the lease has expired.
     */
    @Modifying
    @Query(value = """
        UPDATE idempotency_keys 
        SET status = 'IN_PROGRESS', 
            owner_id = :ownerId, 
            lease_until = :leaseUntil, 
            updated_at = NOW()
        WHERE merchant_id = :merchantId 
          AND idempotency_key = :key 
          AND request_hash = :requestHash
          AND status = 'IN_PROGRESS' 
          AND (lease_until IS NULL OR lease_until < NOW())
    """, nativeQuery = true)
    int reclaimExpired(@Param("merchantId") String merchantId, 
                       @Param("key") String key, 
                       @Param("requestHash") String requestHash, 
                       @Param("ownerId") String ownerId, 
                       @Param("leaseUntil") Instant leaseUntil);

    /** Completes the claim only while the caller still owns its active lease. */
    @Modifying
    @Query(value = """
        UPDATE idempotency_keys
        SET status = 'COMPLETED',
            payment_id = :paymentId,
            response_snapshot = :responseSnapshot,
            response_status = :responseStatus,
            owner_id = NULL,
            lease_until = NULL,
            updated_at = NOW()
        WHERE id = :id
          AND status = 'IN_PROGRESS'
          AND owner_id = :ownerId
          AND lease_until > NOW()
        """, nativeQuery = true)
    int completeIfOwned(@Param("id") Long id,
                        @Param("ownerId") String ownerId,
                        @Param("paymentId") String paymentId,
                        @Param("responseSnapshot") String responseSnapshot,
                        @Param("responseStatus") int responseStatus);
}
