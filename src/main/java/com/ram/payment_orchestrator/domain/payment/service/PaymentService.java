package com.ram.payment_orchestrator.domain.payment.service;

import com.ram.payment_orchestrator.domain.payment.domain.Payment;
import com.ram.payment_orchestrator.domain.payment.domain.PaymentRequest;
import com.ram.payment_orchestrator.domain.payment.domain.PaymentStatus;
import com.ram.payment_orchestrator.domain.payment.persistence.PaymentEntity;
import com.ram.payment_orchestrator.domain.payment.persistence.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.function.UnaryOperator;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment createPayment(PaymentRequest request) {
        Payment payment = Payment.builder()
                .paymentId(UUID.randomUUID().toString())
                .merchantId(request.getMerchantId())
                .merchantRef(request.getMerchantRef())
                .amountMinor(request.getAmountMinor())
                .currency(request.getCurrency())
                .paymentToken(request.getPaymentToken())
                .idempotencyKey(request.getIdempotencyKey())
                .status(PaymentStatus.INITIATED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return save(payment);
    }

    @Transactional
    public Payment routePayment(Payment payment) {
        return transition(payment, PaymentStatus.INITIATED, p -> p.transition(PaymentStatus.ROUTED));
    }

    @Transactional
    public Payment markAsSuccess(Payment payment, String providerOperationId) {
        return transition(payment, PaymentStatus.ROUTED, p -> p.transition(PaymentStatus.SUCCESS, providerOperationId));
    }

    @Transactional
    public Payment markAsFailed(Payment payment) {
        return transition(payment, PaymentStatus.ROUTED, p -> p.transition(PaymentStatus.FAILED));
    }

    @Transactional
    public Payment markAsUnknown(Payment payment, String providerOperationId) {
        return transition(payment, PaymentStatus.ROUTED, p -> p.transition(PaymentStatus.UNKNOWN, providerOperationId));
    }

    // --- Private "Template" Methods ---

    private Payment transition(Payment p, PaymentStatus expected, UnaryOperator<Payment> transitionLogic) {
        if (p.getStatus() != expected) {
            throw new IllegalStateException("Cannot transition payment " + p.getPaymentId() +
                    " from " + p.getStatus() + " while expecting " + expected);
        }
        return save(transitionLogic.apply(p));
    }

    private Payment save(Payment payment) {
        paymentRepository.save(mapToEntity(payment));
        return payment;
    }

    private PaymentEntity mapToEntity(Payment p) {
        return PaymentEntity.builder()
                .paymentId(p.getPaymentId())
                .merchantId(p.getMerchantId())
                .merchantRef(p.getMerchantRef())
                .amountMinor(p.getAmountMinor())
                .currency(p.getCurrency())
                .paymentToken(p.getPaymentToken())
                .idempotencyKey(p.getIdempotencyKey())
                .providerOperationId(p.getProviderOperationId())
                .resolverOwner(p.getResolverOwner())
                .resolverLeaseUntil(p.getResolverLeaseUntil())
                .status(p.getStatus())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
