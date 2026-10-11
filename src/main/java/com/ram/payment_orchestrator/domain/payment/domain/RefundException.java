package com.ram.payment_orchestrator.domain.payment.domain;

public class RefundException extends RuntimeException {

    private final Reason reason;

    public RefundException(Reason reason) {
        super(switch (reason) {
            case PAYMENT_NOT_FOUND -> "Payment not found";
            case PAYMENT_NOT_SUCCESSFUL -> "Only successful payments can be refunded";
            case ALREADY_REFUNDED -> "Payment has already been refunded";
        });
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        PAYMENT_NOT_FOUND,
        PAYMENT_NOT_SUCCESSFUL,
        ALREADY_REFUNDED
    }
}
