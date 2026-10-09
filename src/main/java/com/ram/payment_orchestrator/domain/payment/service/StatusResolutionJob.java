package com.ram.payment_orchestrator.domain.payment.service;

import com.ram.payment_orchestrator.domain.provider.domain.ProviderStatus;
import com.ram.payment_orchestrator.domain.provider.domain.StatusResult;
import com.ram.payment_orchestrator.domain.provider.service.ProviderGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@Slf4j
public class StatusResolutionJob {

    private final PaymentResolutionService resolutionService;
    private final ProviderGateway providerGateway;
    private final long staleAfterSeconds;
    private final long leaseSeconds;
    private final int batchSize;
    private final int maxAttempts;

    public StatusResolutionJob(
            PaymentResolutionService resolutionService,
            ProviderGateway providerGateway,
            @Value("${payment.resolution.stale-after-seconds:60}") long staleAfterSeconds,
            @Value("${payment.resolution.lease-seconds:120}") long leaseSeconds,
            @Value("${payment.resolution.batch-size:100}") int batchSize,
            @Value("${payment.resolution.max-attempts:10}") int maxAttempts) {
        this.resolutionService = resolutionService;
        this.providerGateway = providerGateway;
        this.staleAfterSeconds = staleAfterSeconds;
        this.leaseSeconds = leaseSeconds;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${payment.resolution.interval-ms:30000}")
    public void resolveBatch() {
        String ownerId = UUID.randomUUID().toString();
        var candidates = resolutionService.claimBatch(
                ownerId,
                Duration.ofSeconds(staleAfterSeconds),
                Duration.ofSeconds(leaseSeconds),
                batchSize,
                maxAttempts
        );

        for (PaymentResolutionService.ResolutionCandidate candidate : candidates) {
            try {
                StatusResult result;
                try {
                    result = providerGateway.queryStatus(
                            candidate.providerCode(),
                            candidate.providerOperationId(),
                            candidate.providerRef()
                    );
                } catch (RuntimeException exception) {
                    log.warn("Provider status query failed for payment {}", candidate.paymentId(), exception);
                    result = new StatusResult(ProviderStatus.UNKNOWN, candidate.providerRef());
                }

                resolutionService.finish(candidate, result, maxAttempts);
            } catch (RuntimeException exception) {
                // The lease expires and another scheduled run can reclaim this payment.
                log.error("Could not finalize status resolution for payment {}", candidate.paymentId(), exception);
            }
        }
    }
}
