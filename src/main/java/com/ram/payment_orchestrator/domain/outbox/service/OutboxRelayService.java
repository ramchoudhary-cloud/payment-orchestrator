package com.ram.payment_orchestrator.domain.outbox.service;

import com.ram.payment_orchestrator.domain.outbox.persistence.OutboxEntity;
import com.ram.payment_orchestrator.domain.outbox.persistence.OutboxRepository;
import com.ram.payment_orchestrator.domain.outbox.persistence.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxRelayService {

    private final OutboxRepository outboxRepository;

    @Transactional
    public List<OutboxMessage> claimBatch(String workerId, Duration leaseDuration, int batchSize) {
        Instant now = Instant.now();
        return outboxRepository.findReadyToPublish(now, batchSize).stream()
                .map(event -> {
                    event.setLockedBy(workerId);
                    event.setLockedUntil(now.plus(leaseDuration));
                    event.setAttempts(event.getAttempts() + 1);
                    return new OutboxMessage(
                            event.getId(),
                            event.getAggregateId(),
                            event.getEventType(),
                            event.getPayload(),
                            event.getAttempts()
                    );
                })
                .toList();
    }

    @Transactional
    public boolean markPublished(Long eventId, String workerId) {
        OutboxEntity event = getOwnedEvent(eventId, workerId);
        if (event == null) {
            return false;
        }

        event.setStatus(OutboxStatus.PUBLISHED);
        clearLease(event);
        return true;
    }

    @Transactional
    public boolean scheduleRetry(Long eventId, String workerId, Duration delay) {
        OutboxEntity event = getOwnedEvent(eventId, workerId);
        if (event == null) {
            return false;
        }

        event.setNextAttemptAt(Instant.now().plus(delay));
        clearLease(event);
        return true;
    }

    private OutboxEntity getOwnedEvent(Long eventId, String workerId) {
        OutboxEntity event = outboxRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalStateException("Outbox event not found: " + eventId));
        if (event.getStatus() != OutboxStatus.PENDING
                || !workerId.equals(event.getLockedBy())) {
            return null;
        }
        return event;
    }

    private void clearLease(OutboxEntity event) {
        event.setLockedBy(null);
        event.setLockedUntil(null);
    }

    public record OutboxMessage(
            Long id,
            String aggregateId,
            String eventType,
            String payload,
            int attempts
    ) {
    }
}
