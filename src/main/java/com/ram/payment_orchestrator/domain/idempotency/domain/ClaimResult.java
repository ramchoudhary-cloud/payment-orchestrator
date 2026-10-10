package com.ram.payment_orchestrator.domain.idempotency.domain;

import java.util.Optional;

/**
 * Result of an idempotency claim attempt.
 */
public record ClaimResult(
    Status status,
    Long idempotencyId,
    String ownerId,
    Optional<StoredResponse> storedResponse,
    boolean reclaimed
) {
    public enum Status {
        SUCCESS,        // Claimed successfully, proceed with work
        REPLAY,         // Already completed, replay the attached response
        CONFLICT,       // Hash mismatch or active claim exists
        ERROR           // Unexpected state
    }

    public static ClaimResult success(Long id, String ownerId) {
        return new ClaimResult(Status.SUCCESS, id, ownerId, Optional.empty(), false);
    }

    public static ClaimResult reclaimed(Long id, String ownerId) {
        return new ClaimResult(Status.SUCCESS, id, ownerId, Optional.empty(), true);
    }

    public static ClaimResult replay(StoredResponse response) {
        return new ClaimResult(Status.REPLAY, null, null, Optional.of(response), false);
    }

    public static ClaimResult conflict() {
        return new ClaimResult(Status.CONFLICT, null, null, Optional.empty(), false);
    }
}
