package com.ram.payment_orchestrator.api.payment;

public record PaymentResponse(
        String paymentId,
        String status,
        long amountMinor,
        String currency,
        String provider
) {
}
