package com.ram.payment_orchestrator.domain.outbox.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class OutboxRelayJob {

    private final OutboxRelayService relayService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final long leaseSeconds;
    private final long sendTimeoutMillis;
    private final long retryBaseSeconds;
    private final long retryMaxSeconds;
    private final int batchSize;

    public OutboxRelayJob(
            OutboxRelayService relayService,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${payment.outbox.topic:payment-terminal-events}") String topic,
            @Value("${payment.outbox.lease-seconds:30}") long leaseSeconds,
            @Value("${payment.outbox.send-timeout-ms:5000}") long sendTimeoutMillis,
            @Value("${payment.outbox.retry-base-seconds:2}") long retryBaseSeconds,
            @Value("${payment.outbox.retry-max-seconds:900}") long retryMaxSeconds,
            @Value("${payment.outbox.batch-size:50}") int batchSize
    ) {
        this.relayService = relayService;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.leaseSeconds = leaseSeconds;
        this.sendTimeoutMillis = sendTimeoutMillis;
        this.retryBaseSeconds = retryBaseSeconds;
        this.retryMaxSeconds = retryMaxSeconds;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${payment.outbox.interval-ms:5000}")
    public void publishBatch() {
        String workerId = UUID.randomUUID().toString();
        var messages = relayService.claimBatch(
                workerId,
                Duration.ofSeconds(leaseSeconds),
                batchSize
        );

        for (var message : messages) {
            try {
                kafkaTemplate.send(topic, message.aggregateId(), message.payload())
                        .get(sendTimeoutMillis, TimeUnit.MILLISECONDS);
                relayService.markPublished(message.id(), workerId);
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }

                Duration delay = retryDelay(message.attempts());
                relayService.scheduleRetry(message.id(), workerId, delay);
                log.warn(
                        "Could not publish outbox event {}; retrying after {} seconds",
                        message.id(),
                        delay.toSeconds(),
                        exception
                );
            }
        }
    }

    private Duration retryDelay(int attempts) {
        int exponent = Math.min(Math.max(attempts - 1, 0), 20);
        long delaySeconds = Math.min(retryMaxSeconds, retryBaseSeconds * (1L << exponent));
        return Duration.ofSeconds(delaySeconds);
    }
}
