package com.ram.payment_orchestrator.domain.outbox.domain;

public record PaymentTerminalEvent(
        String paymentId,
        String merchantRef,
        String status,
        long amountMinor,
        String currency,
        String occurredAt
) {
}
