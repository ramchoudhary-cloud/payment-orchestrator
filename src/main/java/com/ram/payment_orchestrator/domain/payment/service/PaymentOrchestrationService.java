package com.ram.payment_orchestrator.domain.payment.service;

import com.ram.payment_orchestrator.domain.payment.domain.Payment;
import com.ram.payment_orchestrator.domain.payment.domain.PaymentRequest;
import com.ram.payment_orchestrator.domain.provider.domain.ChargeRequest;
import com.ram.payment_orchestrator.domain.provider.domain.ChargeResult;
import com.ram.payment_orchestrator.domain.provider.service.ProviderGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentOrchestrationService {

    private static final String PROVIDER_CODE = "PROVIDER_A";

    private final PaymentAttemptPersistenceService attemptPersistenceService;
    private final ProviderGateway providerGateway;
    private final PaymentFinalizationService finalizationService;

    public Payment process(PaymentRequest request) {
        String operationId = UUID.randomUUID().toString();

        // Commits payment + operation identity + attempt before any provider call.
        Payment payment = attemptPersistenceService.prepare(
                request, PROVIDER_CODE, operationId
        );

        ChargeResult result = providerGateway.charge(
                PROVIDER_CODE,
                new ChargeRequest(
                        payment.getAmountMinor(),
                        payment.getCurrency(),
                        payment.getPaymentToken(),
                        payment.getMerchantRef(),
                        operationId
                )
        );

        // Saves the normalized result and local final state after the remote call.
        return finalizationService.finalizeCharge(payment, PROVIDER_CODE, result);
    }
}
