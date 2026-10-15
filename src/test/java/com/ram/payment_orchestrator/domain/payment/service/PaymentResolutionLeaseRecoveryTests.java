package com.ram.payment_orchestrator.domain.payment.service;

import com.ram.payment_orchestrator.TestJsonMapperConfiguration;
import com.ram.payment_orchestrator.domain.ledger.service.LedgerService;
import com.ram.payment_orchestrator.domain.outbox.service.OutboxService;
import com.ram.payment_orchestrator.domain.webhook.service.WebhookDeliveryService;
import com.ram.payment_orchestrator.domain.payment.domain.PaymentStatus;
import com.ram.payment_orchestrator.domain.payment.persistence.PaymentEntity;
import com.ram.payment_orchestrator.domain.payment.persistence.PaymentRepository;
import com.ram.payment_orchestrator.domain.provider.domain.Outcome;
import com.ram.payment_orchestrator.domain.provider.persistence.ProviderAttemptEntity;
import com.ram.payment_orchestrator.domain.provider.persistence.ProviderAttemptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PaymentResolutionService.class, LedgerService.class, OutboxService.class,
        WebhookDeliveryService.class,
        TestJsonMapperConfiguration.class})
class PaymentResolutionLeaseRecoveryTests {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ProviderAttemptRepository attemptRepository;

    @Autowired
    private PaymentResolutionService resolutionService;

    @Test
    void activeLeasePreventsAnotherWorkerFromClaimingPayment() {
        Instant now = Instant.now();
        String paymentId = saveResolvingPayment(
                "active-worker",
                now.plusSeconds(60),
                now.minusSeconds(120),
                1
        );

        var candidates = resolutionService.claimBatch(
                "second-worker",
                Duration.ofSeconds(30),
                Duration.ofSeconds(60),
                10,
                5
        );

        assertTrue(candidates.stream().noneMatch(candidate -> candidate.paymentId().equals(paymentId)));
        assertEquals("active-worker", paymentRepository.findById(paymentId)
                .orElseThrow().getResolverOwner());
    }

    @Test
    void expiredLeaseCanBeClaimedByAnotherWorker() {
        Instant now = Instant.now();
        String paymentId = saveResolvingPayment(
                "crashed-worker",
                now.minusSeconds(60),
                now.minusSeconds(120),
                1
        );

        var candidates = resolutionService.claimBatch(
                "recovery-worker",
                Duration.ofSeconds(30),
                Duration.ofSeconds(60),
                10,
                5
        );

        var candidate = candidates.stream()
                .filter(item -> item.paymentId().equals(paymentId))
                .findFirst()
                .orElseThrow();

        PaymentEntity recovered = paymentRepository.findById(paymentId).orElseThrow();
        assertEquals("recovery-worker", recovered.getResolverOwner());
        assertEquals(2, recovered.getResolutionAttempts());
        assertEquals(candidate.providerOperationId(), recovered.getProviderOperationId());
    }

    @Test
    void stalePaymentAtResolutionLimitMovesToManualReview() {
        Instant now = Instant.now();
        String paymentId = saveResolvingPayment(
                "expired-worker",
                now.minusSeconds(60),
                now.minusSeconds(120),
                5
        );

        var candidates = resolutionService.claimBatch(
                "manual-review-worker",
                Duration.ZERO,
                Duration.ofSeconds(30),
                10,
                5
        );

        PaymentEntity payment = paymentRepository.findById(paymentId).orElseThrow();
        assertEquals(PaymentStatus.NEEDS_MANUAL_REVIEW, payment.getStatus());
        assertNull(payment.getResolverOwner());
        assertNull(payment.getResolverLeaseUntil());
        assertTrue(candidates.stream().noneMatch(item -> item.paymentId().equals(paymentId)));
    }

    private String saveResolvingPayment(
            String owner,
            Instant leaseUntil,
            Instant updatedAt,
            int resolutionAttempts
    ) {
        String paymentId = UUID.randomUUID().toString();
        String operationId = UUID.randomUUID().toString();
        Instant createdAt = updatedAt.minusSeconds(60);

        paymentRepository.saveAndFlush(PaymentEntity.builder()
                .paymentId(paymentId)
                .merchantId("lease-test-merchant")
                .merchantRef("lease-test-" + paymentId)
                .amountMinor(2500L)
                .currency("INR")
                .paymentToken("test-token")
                .idempotencyKey(UUID.randomUUID().toString())
                .providerOperationId(operationId)
                .resolverOwner(owner)
                .resolverLeaseUntil(leaseUntil)
                .resolutionAttempts(resolutionAttempts)
                .status(PaymentStatus.RESOLVING)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build());

        attemptRepository.saveAndFlush(ProviderAttemptEntity.builder()
                .paymentId(paymentId)
                .providerCode("PROVIDER_A")
                .providerOperationId(operationId)
                .outcome(Outcome.TIMEOUT)
                .createdAt(createdAt)
                .build());

        return paymentId;
    }
}
