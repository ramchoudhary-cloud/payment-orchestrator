package com.ram.payment_orchestrator.domain.payment.service;

import com.ram.payment_orchestrator.domain.ledger.service.LedgerService;
import com.ram.payment_orchestrator.domain.payment.domain.Payment;
import com.ram.payment_orchestrator.domain.provider.domain.ChargeResult;
import com.ram.payment_orchestrator.domain.provider.domain.Outcome;
import com.ram.payment_orchestrator.domain.provider.domain.ProviderStatus;
import com.ram.payment_orchestrator.domain.provider.persistence.ProviderAttemptEntity;
import com.ram.payment_orchestrator.domain.provider.persistence.ProviderAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentFinalizationService {

    private final PaymentService paymentService;
    private final ProviderAttemptRepository attemptRepository;
    private final LedgerService ledgerService;

    @Transactional
    public Payment finalizeCharge(Payment payment, String providerCode, ChargeResult result) {
        ProviderAttemptEntity attempt = attemptRepository
                .findByProviderCodeAndProviderOperationId(
                        providerCode,
                        payment.getProviderOperationId()
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Provider attempt not found for operation " + payment.getProviderOperationId()
                ));

        Outcome outcome = normalizeOutcome(result);
        attempt.setOutcome(outcome);
        attempt.setProviderRef(result == null ? null : result.providerRef());
        // The attempt is saved through JPA dirty checking.
        return switch (outcome) {
            case ACCEPTED -> {
                Payment succeeded = paymentService.markAsSuccess(
                        payment, payment.getProviderOperationId()
                );
                ledgerService.recordCapture(succeeded.getPaymentId(), succeeded.getAmountMinor());
                yield succeeded;
            }
            case DECLINED, REJECTED, UNREACHABLE -> paymentService.markAsFailed(payment);
            case TIMEOUT, AMBIGUOUS -> paymentService.markAsUnknown(
                    payment, payment.getProviderOperationId()
            );
        };
    }

    private Outcome normalizeOutcome(ChargeResult result) {
        if (result == null || result.outcome() == null) {
            return Outcome.AMBIGUOUS;
        }
        if (result.outcome() == Outcome.ACCEPTED // success case ACCEPTED + CHARGED
                && result.finalStatus() != ProviderStatus.CHARGED) {
            return Outcome.AMBIGUOUS;
        }
        return result.outcome();
    }
}
