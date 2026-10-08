package com.ram.payment_orchestrator.api.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreatePaymentRequest(
        @NotBlank String merchantRef,
        @NotNull @Positive Long amountMinor,
        @NotBlank String currency,
        @NotBlank String paymentToken
) {
}
