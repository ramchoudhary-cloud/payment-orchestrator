package com.ram.payment_orchestrator.domain.idempotency.domain;

/**
 * Represents the response to be replayed for a completed idempotent request.
 */
public record StoredResponse(int statusCode, String body) {
}
