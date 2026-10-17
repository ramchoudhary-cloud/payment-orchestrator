package com.ram.payment_orchestrator.domain.payment.service;

import com.ram.payment_orchestrator.domain.ledger.service.LedgerService;
import com.ram.payment_orchestrator.domain.outbox.domain.PaymentEventType;
import com.ram.payment_orchestrator.domain.outbox.service.OutboxService;
import com.ram.payment_orchestrator.domain.payment.domain.PaymentStatus;
import com.ram.payment_orchestrator.domain.payment.persistence.PaymentEntity;
import com.ram.payment_orchestrator.domain.payment.persistence.PaymentRepository;
import com.ram.payment_orchestrator.domain.provider.domain.ProviderStatus;
import com.ram.payment_orchestrator.domain.provider.domain.StatusResult;
import com.ram.payment_orchestrator.domain.provider.persistence.ProviderAttemptEntity;
import com.ram.payment_orchestrator.domain.provider.persistence.ProviderAttemptRepository;
import com.ram.payment_orchestrator.domain.payment.persistence.FailoverWorkEntity;
import com.ram.payment_orchestrator.domain.payment.persistence.FailoverWorkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentResolutionService {

    private final PaymentRepository paymentRepository;
    private final ProviderAttemptRepository attemptRepository;
    private final LedgerService ledgerService;
    private final FailoverWorkRepository failoverWorkRepository;
    private final OutboxService outboxService;

    @Transactional
    public List<ResolutionCandidate> claimBatch(
            String ownerId,
            Duration staleAfter,
            Duration leaseDuration,
            int batchSize,
            int maxAttempts) {

        Instant now = Instant.now();
        Instant staleBefore = now.minus(staleAfter);
        List<String> exhaustedPaymentIds = paymentRepository.findExhaustedPaymentIds(
                now, staleBefore, maxAttempts, batchSize
        );
        for (String paymentId : exhaustedPaymentIds) {
            paymentRepository.moveExhaustedToManualReviewById(
                    paymentId, now, staleBefore, maxAttempts
            );
        }

        List<PaymentEntity> payments = paymentRepository.findResolvable(
                now, staleBefore, maxAttempts, batchSize
        );

        return payments.stream()
                .map(payment -> {
                    ProviderAttemptEntity attempt = attemptRepository
                            .findByPaymentIdAndProviderOperationId(
                                    payment.getPaymentId(),
                                    payment.getProviderOperationId()
                            )
                            .orElseThrow(() -> new IllegalStateException(
                                    "Provider attempt not found for payment " + payment.getPaymentId()
                            ));

                    payment.setStatus(PaymentStatus.RESOLVING);
                    payment.setResolverOwner(ownerId);
                    payment.setResolverLeaseUntil(now.plus(leaseDuration));
                    payment.setResolutionAttempts(payment.getResolutionAttempts() + 1);
                    payment.setUpdatedAt(now);

                    return new ResolutionCandidate(
                            payment.getPaymentId(),
                            attempt.getProviderCode(),
                            attempt.getProviderOperationId(),
                            attempt.getProviderRef(),
                            payment.getAmountMinor(),
                            payment.getResolutionAttempts(),
                            ownerId
                    );
                })
                .toList();
    }

    @Transactional
    public void finish(ResolutionCandidate candidate, StatusResult result, int maxAttempts) {
        PaymentEntity payment = paymentRepository.findByIdForUpdate(candidate.paymentId())
                .orElseThrow(() -> new IllegalStateException(
                        "Payment not found: " + candidate.paymentId()
                ));

        if (payment.getStatus() != PaymentStatus.RESOLVING
                || !candidate.ownerId().equals(payment.getResolverOwner())) {
            throw new IllegalStateException(
                    "Resolver no longer owns payment " + candidate.paymentId()
            );
        }

        ProviderAttemptEntity attempt = attemptRepository
                .findByPaymentIdAndProviderOperationId(
                        candidate.paymentId(),
                        candidate.providerOperationId()
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Provider attempt not found for payment " + candidate.paymentId()
                ));

        if (result != null && result.providerRef() != null) {
            attempt.setProviderRef(result.providerRef());
        }

        ProviderStatus status = result == null ? ProviderStatus.UNKNOWN : result.status();
        if (status == null) {
            status = ProviderStatus.UNKNOWN;
        }
        attempt.setResolvedStatus(status);

        switch (status) {
            case CHARGED -> {
                payment.setStatus(PaymentStatus.SUCCESS);
                ledgerService.recordCapture(payment.getPaymentId(), payment.getAmountMinor());
                outboxService.stagePaymentTerminalEvent(
                        payment.getPaymentId(),
                        payment.getMerchantRef(),
                        PaymentStatus.SUCCESS,
                        payment.getAmountMinor(),
                        payment.getCurrency(),
                        PaymentEventType.PAYMENT_SUCCEEDED
                );
            }
            case NOT_CHARGED_DEFINITIVE -> {
                payment.setStatus(PaymentStatus.FAILED);
                failoverWorkRepository.save(FailoverWorkEntity.pending(
                        candidate.paymentId(), candidate.providerOperationId(), Instant.now()
                ));
            }
            case PENDING, UNKNOWN -> payment.setStatus(
                    payment.getResolutionAttempts() >= maxAttempts
                            ? PaymentStatus.NEEDS_MANUAL_REVIEW
                            : PaymentStatus.UNKNOWN
            );
        }

        payment.setResolverOwner(null);
        payment.setResolverLeaseUntil(null);
        payment.setUpdatedAt(Instant.now());
    }

    public record ResolutionCandidate(
            String paymentId,
            String providerCode,
            String providerOperationId,
            String providerRef,
            long amountMinor,
            int resolutionAttempts,
            String ownerId
    ) {
    }
}
