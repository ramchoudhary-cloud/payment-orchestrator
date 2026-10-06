package com.ram.payment_orchestrator.domain.payment.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    @NotBlank(message = "merchantId is required")
    private String merchantId;

    @NotBlank(message = "paymentToken is required")
    private String paymentToken;

    @NotNull(message = "amountMinor is required")
    @Positive(message = "amountMinor must be positive")
    private Long amountMinor;

    @NotBlank(message = "currency is required")
    private String currency;

    @NotBlank(message = "merchantRef is required")
    private String merchantRef;

    @NotBlank(message = "idempotencyKey is required")
    private String idempotencyKey;
}
