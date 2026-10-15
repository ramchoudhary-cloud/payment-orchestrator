package com.ram.payment_orchestrator.domain.webhook.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@Slf4j
public class WebhookDispatcher {

    private final WebhookDeliveryService deliveryService;
    private final WebhookHttpClient httpClient;
    private final int maxAttempts;
    private final long timeoutMillis;
    private final int batchSize;

    public WebhookDispatcher(
            WebhookDeliveryService deliveryService,
            WebhookHttpClient httpClient,
            @Value("${payment.webhook.max-attempts:8}") int maxAttempts,
            @Value("${payment.webhook.timeout-ms:10000}") long timeoutMillis,
            @Value("${payment.webhook.batch-size:50}") int batchSize
    ) {
        this.deliveryService = deliveryService;
        this.httpClient = httpClient;
        this.maxAttempts = maxAttempts;
        this.timeoutMillis = timeoutMillis;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${payment.webhook.interval-ms:500}")
    public void dispatchReady() {
        String workerId = UUID.randomUUID().toString();
        var messages = deliveryService.claimBatch(
                workerId, Duration.ofSeconds(30), batchSize, maxAttempts
        );

        for (var message : messages) {
            try {
                httpClient.deliver(message.endpointUrl(), message.payload(),
                        Duration.ofMillis(timeoutMillis));
                deliveryService.markDelivered(message.id(), workerId);
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                Duration retryDelay = retryDelay(message.attempts());
                deliveryService.scheduleRetry(message.id(), workerId, maxAttempts, retryDelay);
                log.warn("Webhook delivery {} failed; retrying in {} seconds",
                        message.id(), retryDelay.toSeconds(), exception);
            }
        }
    }

    private Duration retryDelay(int attempts) {
        int exponent = Math.min(Math.max(attempts - 1, 0), 20);
        return Duration.ofSeconds(1L << exponent);
    }
}
