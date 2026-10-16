package com.ram.payment_orchestrator.domain.outbox.service;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayJobTests {

    @SuppressWarnings({"rawtypes", "unchecked"})
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

    private final OutboxRelayService relayService = mock(OutboxRelayService.class);
    private final OutboxRelayJob job = new OutboxRelayJob(
            relayService, kafkaTemplate, "payment-events", 30, 5000, 2, 900, 50
    );

    @Test
    void successfulKafkaPublishMarksEventPublished() {
        var event = new OutboxRelayService.OutboxMessage(7L, "payment-7", "payment.succeeded", "{}", 1);
        when(relayService.claimBatch(any(), any(), eq(50))).thenReturn(List.of(event));
        when(kafkaTemplate.send("payment-events", "payment-7", "{}"))
                .thenReturn(CompletableFuture.completedFuture(null));

        job.publishBatch();

        verify(relayService).markPublished(eq(7L), any());
    }

    @Test
    void failedKafkaPublishSchedulesExponentialRetry() {
        var event = new OutboxRelayService.OutboxMessage(8L, "payment-8", "payment.failed", "{}", 2);
        when(relayService.claimBatch(any(), any(), eq(50))).thenReturn(List.of(event));
        when(kafkaTemplate.send("payment-events", "payment-8", "{}"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        job.publishBatch();

        verify(relayService).scheduleRetry(eq(8L), any(), eq(Duration.ofSeconds(4)));
    }
}
