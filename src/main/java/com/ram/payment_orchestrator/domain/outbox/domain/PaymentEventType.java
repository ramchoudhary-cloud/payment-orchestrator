package com.ram.payment_orchestrator.domain.outbox.domain;

public enum PaymentEventType {
    PAYMENT_SUCCEEDED("payment.succeeded"),
    PAYMENT_FAILED("payment.failed");

    private final String value;

    PaymentEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
