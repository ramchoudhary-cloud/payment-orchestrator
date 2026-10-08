package com.ram.payment_orchestrator.domain.provider.domain;

public record ChargeRequest(
        long amountMinor,
        String currency,
        String paymentToken,
        String merchantRef,
        String providerOperationId) {
}
