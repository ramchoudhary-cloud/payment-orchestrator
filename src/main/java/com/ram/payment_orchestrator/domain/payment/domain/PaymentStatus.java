package com.ram.payment_orchestrator.domain.payment.domain;

public enum PaymentStatus {
    INITIATED,
    ROUTED,
    SUCCESS,
    FAILED,
    UNKNOWN,
    RESOLVING,
    NEEDS_MANUAL_REVIEW
}
