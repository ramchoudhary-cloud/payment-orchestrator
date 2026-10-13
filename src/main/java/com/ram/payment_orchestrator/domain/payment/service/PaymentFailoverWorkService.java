package com.ram.payment_orchestrator.domain.payment.service;

import com.ram.payment_orchestrator.domain.payment.domain.Payment;
import com.ram.payment_orchestrator.domain.payment.domain.PaymentStatus;
import com.ram.payment_orchestrator.domain.payment.persistence.FailoverWorkEntity;
import com.ram.payment_orchestrator.domain.payment.persistence.FailoverWorkRepository;
import com.ram.payment_orchestrator.domain.payment.persistence.FailoverWorkStatus;
import com.ram.payment_orchestrator.domain.payment.persistence.PaymentEntity;
import com.ram.payment_orchestrator.domain.payment.persistence.PaymentRepository;
import com.ram.payment_orchestrator.domain.provider.domain.ProviderStatus;
import com.ram.payment_orchestrator.domain.provider.persistence.ProviderAttemptEntity;
import com.ram.payment_orchestrator.domain.provider.persistence.ProviderAttemptRepository;
import com.ram.payment_orchestrator.domain.provider.service.RoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentFailoverWorkService {

    private final FailoverWorkRepository workRepository;
    private final PaymentRepository paymentRepository;
    private final ProviderAttemptRepository attemptRepository;
    private final RoutingService routingService;

    @Transactional
    public List<WorkClaim> claimBatch(String ownerId, Duration leaseDuration, int batchSize) {
        Instant now = Instant.now();
        List<FailoverWorkEntity> workItems = workRepository.findClaimable(now, batchSize);
        return workItems.stream().map(work -> {
            work.setStatus(FailoverWorkStatus.CLAIMED);
            work.setOwnerId(ownerId);
            work.setLeaseUntil(now.plus(leaseDuration));
            work.setUpdatedAt(now);
            return new WorkClaim(work.getId(), work.getPaymentId(), work.getSourceOperationId(), ownerId);
        }).toList();
    }

    @Transactional
    public Optional<PreparedAttempt> prepareNextAttempt(WorkClaim claim) {
        FailoverWorkEntity work = workRepository.findByIdForUpdate(claim.workId())
                .orElseThrow(() -> new IllegalStateException("Failover work not found: " + claim.workId()));
        if (work.getStatus() != FailoverWorkStatus.CLAIMED
                || !claim.ownerId().equals(work.getOwnerId())) {
            return Optional.empty();
        }

        PaymentEntity payment = paymentRepository.findByIdForUpdate(claim.paymentId())
                .orElseThrow(() -> new IllegalStateException("Payment not found: " + claim.paymentId()));
        if (payment.getStatus() != PaymentStatus.FAILED
                || !claim.sourceOperationId().equals(payment.getProviderOperationId())) {
            complete(work);
            return Optional.empty();
        }

        ProviderAttemptEntity sourceAttempt = attemptRepository
                .findByPaymentIdAndProviderOperationId(claim.paymentId(), claim.sourceOperationId())
                .orElseThrow(() -> new IllegalStateException(
                        "Source provider attempt not found: " + claim.sourceOperationId()));
        if (sourceAttempt.getResolvedStatus() != ProviderStatus.NOT_CHARGED_DEFINITIVE) {
            complete(work);
            return Optional.empty();
        }

        List<ProviderAttemptEntity> attempts = attemptRepository
                .findByPaymentIdOrderByCreatedAtDesc(claim.paymentId());
        Set<String> attemptedProviders = new LinkedHashSet<>();
        attempts.forEach(attempt -> attemptedProviders.add(attempt.getProviderCode()));
        Optional<String> selectedProvider = routingService.selectNextProvider(attemptedProviders);
        if (selectedProvider.isEmpty()) {
            // Keep the work pending so a later run can retry after a circuit closes.
            work.setStatus(FailoverWorkStatus.PENDING);
            work.setOwnerId(null);
            work.setLeaseUntil(null);
            work.setUpdatedAt(Instant.now());
            return Optional.empty();
        }

        String providerCode = selectedProvider.get();
        String operationId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        payment.setStatus(PaymentStatus.ROUTED);
        payment.setProviderOperationId(operationId);
        payment.setResolverOwner(null);
        payment.setResolverLeaseUntil(null);
        payment.setUpdatedAt(now);

        attemptRepository.save(ProviderAttemptEntity.builder()
                .paymentId(payment.getPaymentId())
                .providerCode(providerCode)
                .providerOperationId(operationId)
                .outcome(null)
                .createdAt(now)
                .build());

        // The operation and attempt are durable before dispatch. If the process stops
        // after commit, the resolver queries this operation instead of charging again.
        complete(work);
        Payment preparedPayment = toDomain(payment);
        return Optional.of(new PreparedAttempt(preparedPayment, providerCode, attemptedProviders));
    }

    private void complete(FailoverWorkEntity work) {
        work.setStatus(FailoverWorkStatus.COMPLETED);
        work.setOwnerId(null);
        work.setLeaseUntil(null);
        work.setUpdatedAt(Instant.now());
    }

    private Payment toDomain(PaymentEntity entity) {
        return Payment.builder()
                .paymentId(entity.getPaymentId())
                .merchantId(entity.getMerchantId())
                .merchantRef(entity.getMerchantRef())
                .amountMinor(entity.getAmountMinor())
                .currency(entity.getCurrency())
                .paymentToken(entity.getPaymentToken())
                .idempotencyKey(entity.getIdempotencyKey())
                .providerOperationId(entity.getProviderOperationId())
                .resolverOwner(entity.getResolverOwner())
                .resolverLeaseUntil(entity.getResolverLeaseUntil())
                .resolutionAttempts(entity.getResolutionAttempts())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public record WorkClaim(Long workId, String paymentId, String sourceOperationId, String ownerId) { }

    public record PreparedAttempt(Payment payment, String providerCode, Set<String> attemptedProviders) { }
}
