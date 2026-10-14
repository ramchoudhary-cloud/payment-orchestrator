package com.ram.payment_orchestrator.domain.payment.service;

import com.ram.payment_orchestrator.domain.payment.domain.Payment;
import com.ram.payment_orchestrator.domain.payment.domain.PaymentRequest;
import com.ram.payment_orchestrator.domain.payment.domain.RefundException;
import com.ram.payment_orchestrator.domain.payment.domain.PaymentStatus;
import com.ram.payment_orchestrator.domain.payment.persistence.PaymentEntity;
import com.ram.payment_orchestrator.domain.payment.persistence.PaymentRepository;
import com.ram.payment_orchestrator.domain.idempotency.domain.StoredResponse;
import com.ram.payment_orchestrator.domain.idempotency.service.IdempotencyService;
import com.ram.payment_orchestrator.domain.ledger.service.LedgerService;
import com.ram.payment_orchestrator.domain.provider.domain.ChargeRequest;
import com.ram.payment_orchestrator.domain.provider.domain.ChargeResult;
import com.ram.payment_orchestrator.domain.provider.domain.Outcome;
import com.ram.payment_orchestrator.domain.provider.service.ProviderGateway;
import com.ram.payment_orchestrator.domain.provider.service.RoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class PaymentOrchestrationService {

    private final PaymentAttemptPersistenceService attemptPersistenceService;
    private final ProviderGateway providerGateway;
    private final RoutingService routingService;
    private final PaymentFailoverService failoverService;
    private final PaymentFinalizationService finalizationService;
    private final PaymentRepository paymentRepository;
    private final LedgerService ledgerService;
    private final IdempotencyService idempotencyService;

    @Transactional
    public void refund(
            String merchantId,
            String paymentId,
            Long idempotencyId,
            String ownerId,
            StoredResponse response
    ) {
        PaymentEntity payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new RefundException(RefundException.Reason.PAYMENT_NOT_FOUND));

        if (!payment.getMerchantId().equals(merchantId)) {
            throw new RefundException(RefundException.Reason.PAYMENT_NOT_FOUND);
        }
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new RefundException(RefundException.Reason.PAYMENT_NOT_SUCCESSFUL);
        }
        if (ledgerService.hasRefund(paymentId)) {
            throw new RefundException(RefundException.Reason.ALREADY_REFUNDED);
        }

        ledgerService.recordRefund(paymentId, payment.getAmountMinor());
        idempotencyService.complete(idempotencyId, ownerId, paymentId, response);
    }

    public Payment process(PaymentRequest request) {
        Set<String> attemptedProviders = new HashSet<>();
        String providerCode = routingService.selectProvider(attemptedProviders);
        String operationId = UUID.randomUUID().toString();

        // Commits payment + operation identity + attempt before any provider call.
        Payment payment = attemptPersistenceService.prepare(
                request, providerCode, operationId
        );

        return continueFromPreparedAttempt(payment, providerCode, attemptedProviders);
    }

    public Payment continueFromPreparedAttempt(
            Payment payment,
            String providerCode,
            Set<String> attemptedProviders
    ) {
        Set<String> providersAlreadyTried = new HashSet<>(attemptedProviders);

        while (true) {
            ChargeResult result = providerGateway.charge(
                    providerCode,
                    new ChargeRequest(
                            payment.getAmountMinor(),
                            payment.getCurrency(),
                            payment.getPaymentToken(),
                            payment.getMerchantRef(),
                            payment.getProviderOperationId()
                    )
            );

            if (!isDefinitiveNoCharge(result)) {
                // ACCEPTED finalizes successfully; TIMEOUT/AMBIGUOUS remain UNKNOWN.
                return finalizationService.finalizeCharge(payment, providerCode, result);
            }

            providersAlreadyTried.add(providerCode);
            Optional<String> nextProvider = routingService.selectNextProvider(providersAlreadyTried);
            if (nextProvider.isEmpty()) {
                return finalizationService.finalizeCharge(payment, providerCode, result);
            }

            String nextProviderCode = nextProvider.get();
            String nextOperationId = UUID.randomUUID().toString();
            payment = failoverService.prepareNextAttempt(
                    payment,
                    providerCode,
                    result,
                    nextProviderCode,
                    nextOperationId
            );
            providerCode = nextProviderCode;
        }
    }

    private boolean isDefinitiveNoCharge(ChargeResult result) {
        return result != null
                && (result.outcome() == Outcome.DECLINED
                || result.outcome() == Outcome.REJECTED
                || result.outcome() == Outcome.UNREACHABLE);
    }
}
