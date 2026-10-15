package com.ram.payment_orchestrator.domain.webhook.service;

import com.ram.payment_orchestrator.domain.webhook.persistence.WebhookDeliveryEntity;
import com.ram.payment_orchestrator.domain.webhook.persistence.WebhookDeliveryRepository;
import com.ram.payment_orchestrator.domain.webhook.persistence.WebhookDeliveryStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WebhookDeliveryService {

    private final WebhookDeliveryRepository repository;

    @Value("${payment.webhook.endpoint-url:http://localhost:8081/webhooks/payments}")
    private String endpointUrl;

    @Transactional(propagation = Propagation.MANDATORY)
    public void stage(String paymentId, Long eventId, String payload, Instant now) {
        repository.save(WebhookDeliveryEntity.builder()
                .paymentId(paymentId)
                .eventId(eventId)
                .endpointUrl(endpointUrl)
                .payload(payload)
                .status(WebhookDeliveryStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .build());
    }

    @Transactional
    public List<WebhookMessage> claimBatch(
            String workerId,
            Duration leaseDuration,
            int batchSize,
            int maxAttempts
    ) {
        Instant now = Instant.now();
        repository.moveExhaustedToDeadLetter(now, maxAttempts);
        return repository.findReadyToDeliver(now, maxAttempts, batchSize).stream()
                .map(delivery -> {
                    delivery.setLockedBy(workerId);
                    delivery.setLockedUntil(now.plus(leaseDuration));
                    delivery.setAttempts(delivery.getAttempts() + 1);
                    return new WebhookMessage(
                            delivery.getId(), delivery.getEventId(), delivery.getEndpointUrl(),
                            delivery.getPayload(), delivery.getAttempts()
                    );
                })
                .toList();
    }

    @Transactional
    public boolean markDelivered(Long deliveryId, String workerId) {
        WebhookDeliveryEntity delivery = ownedDelivery(deliveryId, workerId);
        if (delivery == null) {
            return false;
        }
        delivery.setStatus(WebhookDeliveryStatus.DELIVERED);
        clearLease(delivery);
        return true;
    }

    @Transactional
    public boolean scheduleRetry(
            Long deliveryId,
            String workerId,
            int maxAttempts,
            Duration retryDelay
    ) {
        WebhookDeliveryEntity delivery = ownedDelivery(deliveryId, workerId);
        if (delivery == null) {
            return false;
        }
        if (delivery.getAttempts() >= maxAttempts) {
            delivery.setStatus(WebhookDeliveryStatus.DEAD_LETTER);
        } else {
            delivery.setNextAttemptAt(Instant.now().plus(retryDelay));
        }
        clearLease(delivery);
        return true;
    }

    private WebhookDeliveryEntity ownedDelivery(Long deliveryId, String workerId) {
        WebhookDeliveryEntity delivery = repository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new IllegalStateException("Webhook delivery not found: " + deliveryId));
        if (delivery.getStatus() != WebhookDeliveryStatus.PENDING
                || !workerId.equals(delivery.getLockedBy())) {
            return null;
        }
        return delivery;
    }

    private void clearLease(WebhookDeliveryEntity delivery) {
        delivery.setLockedBy(null);
        delivery.setLockedUntil(null);
    }

    public record WebhookMessage(
            Long id,
            Long eventId,
            String endpointUrl,
            String payload,
            int attempts
    ) {
    }
}
