package com.ram.payment_orchestrator.domain.outbox.service;

import com.ram.payment_orchestrator.domain.outbox.domain.PaymentEventType;
import com.ram.payment_orchestrator.domain.outbox.domain.PaymentTerminalEvent;
import com.ram.payment_orchestrator.domain.outbox.persistence.OutboxEntity;
import com.ram.payment_orchestrator.domain.outbox.persistence.OutboxRepository;
import com.ram.payment_orchestrator.domain.payment.domain.Payment;
import com.ram.payment_orchestrator.domain.payment.domain.PaymentStatus;
import com.ram.payment_orchestrator.domain.webhook.service.WebhookDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final WebhookDeliveryService webhookDeliveryService;
    private final JsonMapper jsonMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void stagePaymentTerminalEvent(Payment payment, PaymentEventType eventType) {
        stagePaymentTerminalEvent(
                payment.getPaymentId(),
                payment.getMerchantRef(),
                payment.getStatus(),
                payment.getAmountMinor(),
                payment.getCurrency(),
                eventType
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void stagePaymentTerminalEvent(
            String paymentId,
            String merchantRef,
            PaymentStatus status,
            long amountMinor,
            String currency,
            PaymentEventType eventType
    ) {
        PaymentStatus expectedStatus = switch (eventType) {
            case PAYMENT_SUCCEEDED -> PaymentStatus.SUCCESS;
            case PAYMENT_FAILED -> PaymentStatus.FAILED;
        };
        if (status != expectedStatus) {
            throw new IllegalArgumentException(
                    "Event " + eventType.value() + " does not match payment status " + status
            );
        }

        Instant now = Instant.now();
        PaymentTerminalEvent event = new PaymentTerminalEvent(
                paymentId,
                merchantRef,
                status.name(),
                amountMinor,
                currency,
                now.toString()
        );

        try {
            String payload = jsonMapper.writeValueAsString(event);
            OutboxEntity outboxEvent = outboxRepository.save(OutboxEntity.pending(
                    paymentId, eventType.value(), payload, now
            ));
            webhookDeliveryService.stage(paymentId, outboxEvent.getId(), payload, now);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Could not serialize terminal payment event for " + paymentId,
                    exception
            );
        }
    }
}
