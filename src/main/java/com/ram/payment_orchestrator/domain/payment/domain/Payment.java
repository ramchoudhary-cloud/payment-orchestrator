package com.ram.payment_orchestrator.domain.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Payment {
    private final String paymentId;
    private final String merchantId;
    private final String merchantRef;
    private final long amountMinor;
    private final String currency;
    private final String paymentToken;
    private final String idempotencyKey;
    private final String providerOperationId; // From PSP after charge
    private final String resolverOwner;       // Who owns the UNKNOWN resolution
    private final Instant resolverLeaseUntil; // When ownership expires
    private final PaymentStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Payment transition(PaymentStatus nextStatus) {
        return this.toBuilder()
                .status(nextStatus)
                .updatedAt(Instant.now())
                .build();
    }

    public Payment transition(PaymentStatus nextStatus, String providerOperationId) {
        return this.toBuilder()
                .status(nextStatus)
                .providerOperationId(providerOperationId)
                .updatedAt(Instant.now())
                .build();
    }
}
