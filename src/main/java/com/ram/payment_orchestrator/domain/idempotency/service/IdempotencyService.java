package com.ram.payment_orchestrator.domain.idempotency.service;

import com.ram.payment_orchestrator.domain.idempotency.domain.ClaimResult;
import com.ram.payment_orchestrator.domain.idempotency.domain.IdempotencyStatus;
import com.ram.payment_orchestrator.domain.idempotency.domain.StoredResponse;
import com.ram.payment_orchestrator.domain.idempotency.persistence.IdempotencyKeyEntity;
import com.ram.payment_orchestrator.domain.idempotency.persistence.IdempotencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRepository repository;
    private static final Duration LEASE_DURATION = Duration.ofMinutes(2);

    /**
     * Attempts to claim an idempotency key for a specific merchant and request hash.
     */
    @Transactional
    public ClaimResult claim(String merchantId, String key, String requestHash) {
        Optional<IdempotencyKeyEntity> existing = repository.findByMerchantIdAndIdempotencyKey(merchantId, key);

        if (existing.isEmpty()) {
            return tryCreateNewClaim(merchantId, key, requestHash); // key not exist -> new request
        }

        IdempotencyKeyEntity entity = existing.get();

        // A merchant/key pair is permanently bound to its original request, even after lease expiry.
        if (!entity.getRequestHash().equals(requestHash)) {
            log.warn("Idempotency conflict: Hash mismatch for key {} for merchant {}", key, merchantId);
            return ClaimResult.conflict();
        }

        // 1. If completed, verify hash and replay
        if (entity.getStatus() == IdempotencyStatus.COMPLETED) {
            return ClaimResult.replay(new StoredResponse(entity.getResponseStatus(), entity.getResponseSnapshot())); // legitimate retry
        }

        // 2. If IN_PROGRESS, check lease
        if (entity.getLeaseUntil() != null && entity.getLeaseUntil().isAfter(Instant.now())) {
            log.info("Idempotency busy: Key {} for merchant {} has an active lease", key, merchantId);
            return ClaimResult.conflict();
        }

        // 3. Try to reclaim expired lease
        String ownerId = UUID.randomUUID().toString();
        Instant leaseUntil = Instant.now().plus(LEASE_DURATION);
        int updated = repository.reclaimExpired(merchantId, key, requestHash, ownerId, leaseUntil);

        if (updated > 0) {
            return ClaimResult.reclaimed(entity.getId(), ownerId);
        }

        return ClaimResult.conflict();
    }

    private ClaimResult tryCreateNewClaim(String merchantId, String key, String requestHash) { // new request
        String ownerId = UUID.randomUUID().toString();
        Instant leaseUntil = Instant.now().plus(LEASE_DURATION);

        IdempotencyKeyEntity newKey = IdempotencyKeyEntity.builder()
                .merchantId(merchantId)
                .idempotencyKey(key)
                .requestHash(requestHash)
                .status(IdempotencyStatus.IN_PROGRESS)
                .ownerId(ownerId)
                .leaseUntil(leaseUntil)
                .build();

        try {
            IdempotencyKeyEntity saved = repository.save(newKey);
            return ClaimResult.success(saved.getId(), ownerId);
        } catch (DataIntegrityViolationException e) {
            // Concurrent insert won, fallback to re-claiming or replaying
            return claim(merchantId, key, requestHash);
        }
    }

    /**
     * Marks a claimed idempotency key as COMPLETED with the given response.
     */
    @Transactional
    public void complete(Long idempotencyId, String ownerId, String paymentId, StoredResponse response) {
        int updated = repository.completeIfOwned(
                idempotencyId, ownerId, paymentId, response.body(), response.statusCode());
        if (updated == 0) {
            throw new IllegalStateException("Idempotency claim is no longer owned or its lease has expired: " + idempotencyId);
        }
    }
}
