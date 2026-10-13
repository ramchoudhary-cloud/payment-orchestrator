package com.ram.payment_orchestrator.domain.payment.service;

import com.ram.payment_orchestrator.domain.payment.domain.Payment;
import com.ram.payment_orchestrator.domain.payment.domain.PaymentStatus;
import com.ram.payment_orchestrator.domain.payment.persistence.PaymentEntity;
import com.ram.payment_orchestrator.domain.payment.persistence.PaymentRepository;
import com.ram.payment_orchestrator.domain.provider.domain.ChargeResult;
import com.ram.payment_orchestrator.domain.provider.domain.Outcome;
import com.ram.payment_orchestrator.domain.provider.persistence.ProviderAttemptEntity;
import com.ram.payment_orchestrator.domain.provider.persistence.ProviderAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaymentFailoverService {

    private static final Set<Outcome> DEFINITIVE_NO_CHARGE_OUTCOMES =
            Set.of(Outcome.DECLINED, Outcome.REJECTED, Outcome.UNREACHABLE);

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final ProviderAttemptRepository attemptRepository;

    @Transactional
    public Payment prepareNextAttempt(
            Payment payment,
            String failedProvider,
            ChargeResult result,
            String nextProvider,
            String nextOperationId
    ) {
        if (payment.getStatus() != PaymentStatus.ROUTED) {
            throw new IllegalStateException(
                    "Cannot fail over payment from " + payment.getStatus()
            );
        }

        if (result == null
                || !DEFINITIVE_NO_CHARGE_OUTCOMES.contains(result.outcome())) {
            throw new IllegalArgumentException(
                    "Failover requires a definitive no-charge provider outcome"
            );
        }

        PaymentEntity persistedPayment = paymentRepository.findByIdForUpdate(payment.getPaymentId())
                .orElseThrow(() -> new IllegalStateException(
                        "Payment not found: " + payment.getPaymentId()
        ));
        if (persistedPayment.getStatus() != PaymentStatus.ROUTED
                || !Objects.equals(payment.getProviderOperationId(),
                        persistedPayment.getProviderOperationId())) {
            throw new IllegalStateException(
                    "Payment no longer has the routed provider operation being failed over"
            );
        }

        ProviderAttemptEntity failedAttempt = attemptRepository
                .findByPaymentIdAndProviderOperationId(
                        payment.getPaymentId(),
                        payment.getProviderOperationId()
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Provider attempt not found for operation "
                                + payment.getProviderOperationId()
                ));

        if (!failedProvider.equals(failedAttempt.getProviderCode())) {
            throw new IllegalStateException("Provider attempt does not match failed provider");
        }

        failedAttempt.setOutcome(result.outcome());
        failedAttempt.setProviderRef(result.providerRef());

        Payment failedPayment = paymentService.markAsFailed(payment);
        Payment reroutedPayment = paymentService.routePaymentAfterDefinitiveFailure(
                failedPayment,
                nextOperationId
        );

        attemptRepository.save(ProviderAttemptEntity.builder()
                .paymentId(reroutedPayment.getPaymentId())
                .providerCode(nextProvider)
                .providerOperationId(nextOperationId)
                .outcome(null)
                .createdAt(Instant.now())
                .build());

        return reroutedPayment;
    }
}
