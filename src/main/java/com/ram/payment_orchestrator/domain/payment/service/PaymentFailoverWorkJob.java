package com.ram.payment_orchestrator.domain.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@Slf4j
public class PaymentFailoverWorkJob {

    private final PaymentFailoverWorkService workService;
    private final PaymentOrchestrationService orchestrationService;
    private final long leaseSeconds;
    private final int batchSize;

    public PaymentFailoverWorkJob(
            PaymentFailoverWorkService workService,
            PaymentOrchestrationService orchestrationService,
            @Value("${payment.failover.lease-seconds:120}") long leaseSeconds,
            @Value("${payment.failover.batch-size:50}") int batchSize
    ) {
        this.workService = workService;
        this.orchestrationService = orchestrationService;
        this.leaseSeconds = leaseSeconds;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${payment.failover.interval-ms:15000}")
    public void processBatch() {
        String ownerId = UUID.randomUUID().toString();
        var claims = workService.claimBatch(ownerId, Duration.ofSeconds(leaseSeconds), batchSize);
        for (PaymentFailoverWorkService.WorkClaim claim : claims) {
            try {
                workService.prepareNextAttempt(claim).ifPresent(prepared ->
                        orchestrationService.continueFromPreparedAttempt(
                                prepared.payment(), prepared.providerCode(), prepared.attemptedProviders()
                        )
                );
            } catch (RuntimeException exception) {
                // A prepared operation is already durable; the resolver will query it
                // after it becomes stale if dispatch/finalization did not complete.
                log.error("Could not process failover work {} for payment {}",
                        claim.workId(), claim.paymentId(), exception);
            }
        }
    }
}
