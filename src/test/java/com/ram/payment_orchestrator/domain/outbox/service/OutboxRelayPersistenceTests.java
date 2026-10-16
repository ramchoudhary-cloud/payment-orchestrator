package com.ram.payment_orchestrator.domain.outbox.service;

import com.ram.payment_orchestrator.domain.outbox.persistence.OutboxEntity;
import com.ram.payment_orchestrator.domain.outbox.persistence.OutboxRepository;
import com.ram.payment_orchestrator.domain.outbox.persistence.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OutboxRelayService.class)
class OutboxRelayPersistenceTests {

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxRelayService relayService;

    @Test
    void claimCanBeReclaimedAfterLeaseExpiresAndPublishedByCurrentOwner() {
        String aggregateId = UUID.randomUUID().toString();
        OutboxEntity saved = outboxRepository.saveAndFlush(OutboxEntity.pending(
                aggregateId, "payment.succeeded", "{}", Instant.now().minusSeconds(5)
        ));

        var firstClaim = relayService.claimBatch("worker-one", Duration.ofSeconds(60), 10000)
                .stream().filter(message -> message.id().equals(saved.getId())).findFirst().orElseThrow();
        assertEquals(1, firstClaim.attempts());
        assertFalse(relayService.markPublished(saved.getId(), "wrong-worker"));

        OutboxEntity expired = outboxRepository.findById(saved.getId()).orElseThrow();
        expired.setLockedUntil(Instant.now().minusSeconds(1));
        outboxRepository.flush();

        var secondClaim = relayService.claimBatch("worker-two", Duration.ofSeconds(60), 10000)
                .stream().filter(message -> message.id().equals(saved.getId())).findFirst().orElseThrow();
        assertEquals(2, secondClaim.attempts());
        assertTrue(relayService.markPublished(saved.getId(), "worker-two"));

        OutboxEntity published = outboxRepository.findById(saved.getId()).orElseThrow();
        assertEquals(OutboxStatus.PUBLISHED, published.getStatus());
        assertEquals(null, published.getLockedBy());
    }

    @Test
    void retryMovesEventOutOfReadySetUntilScheduledTime() {
        String aggregateId = UUID.randomUUID().toString();
        OutboxEntity saved = outboxRepository.saveAndFlush(OutboxEntity.pending(
                aggregateId, "payment.failed", "{}", Instant.now().minusSeconds(5)
        ));
        relayService.claimBatch("retry-worker", Duration.ofSeconds(60), 10000);

        assertTrue(relayService.scheduleRetry(saved.getId(), "retry-worker", Duration.ofHours(1)));

        OutboxEntity rescheduled = outboxRepository.findById(saved.getId()).orElseThrow();
        assertTrue(rescheduled.getNextAttemptAt().isAfter(Instant.now()));
        assertEquals(null, rescheduled.getLockedBy());
    }
}
