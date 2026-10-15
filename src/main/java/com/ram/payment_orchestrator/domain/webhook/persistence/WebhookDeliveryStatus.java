package com.ram.payment_orchestrator.domain.webhook.persistence;

public enum WebhookDeliveryStatus {
    PENDING,
    DELIVERED,
    DEAD_LETTER
}
