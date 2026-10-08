package com.ram.payment_orchestrator.domain.provider.domain;

public record ChargeResult(
        Outcome outcome,
        ProviderStatus finalStatus,
        String providerRef) {
}
