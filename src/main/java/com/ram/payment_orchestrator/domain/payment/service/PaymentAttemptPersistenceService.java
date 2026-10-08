package com.ram.payment_orchestrator.domain.payment.service;

import com.ram.payment_orchestrator.domain.payment.domain.Payment;
import com.ram.payment_orchestrator.domain.payment.domain.PaymentRequest;
import com.ram.payment_orchestrator.domain.provider.persistence.ProviderAttemptEntity;
import com.ram.payment_orchestrator.domain.provider.persistence.ProviderAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PaymentAttemptPersistenceService {

    private final PaymentService paymentService;
    private final ProviderAttemptRepository attemptRepository;

    @Transactional
    public Payment prepare(PaymentRequest request, String providerCode, String operationId) {
        Payment initiated = paymentService.createPayment(request);
        Payment routed = paymentService.routePayment(initiated, operationId);

        attemptRepository.save(ProviderAttemptEntity.builder()
                .paymentId(routed.getPaymentId())
                .providerCode(providerCode)
                .providerOperationId(operationId)
                .outcome(null)
                .createdAt(Instant.now())
                .build());

        return routed;
    }
}
