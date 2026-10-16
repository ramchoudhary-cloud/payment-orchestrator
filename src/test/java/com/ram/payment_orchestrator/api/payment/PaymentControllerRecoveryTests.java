package com.ram.payment_orchestrator.api.payment;

import com.ram.payment_orchestrator.domain.idempotency.domain.ClaimResult;
import com.ram.payment_orchestrator.domain.idempotency.domain.StoredResponse;
import com.ram.payment_orchestrator.domain.idempotency.service.IdempotencyService;
import com.ram.payment_orchestrator.domain.payment.domain.Payment;
import com.ram.payment_orchestrator.domain.payment.domain.PaymentStatus;
import com.ram.payment_orchestrator.domain.payment.service.PaymentAttemptPersistenceService;
import com.ram.payment_orchestrator.domain.payment.service.PaymentOrchestrationService;
import com.ram.payment_orchestrator.domain.payment.service.PaymentService;
import com.ram.payment_orchestrator.security.MerchantIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerRecoveryTests {

    @Mock
    private MerchantIdentity merchantIdentity;

    @Mock
    private PaymentRequestHasher requestHasher;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private PaymentOrchestrationService orchestrationService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentAttemptPersistenceService attemptPersistenceService;

    private PaymentController controller;

    @BeforeEach
    void setUp() {
        controller = new PaymentController(
                merchantIdentity,
                requestHasher,
                idempotencyService,
                orchestrationService,
                paymentService,
                attemptPersistenceService,
                JsonMapper.builder().build()
        );
    }

    @Test
    void reclaimedClaimWithExistingPaymentReusesPaymentWithoutChargingAgain() {
        String merchantId = "merchant-1";
        String idempotencyKey = "retry-key";
        String ownerId = "recovery-owner";

        CreatePaymentRequest request = new CreatePaymentRequest(
                "order-123", 2500L, "INR", "token-abc");
        Payment existingPayment = payment(PaymentStatus.ROUTED);

        when(merchantIdentity.requireMerchantId(any())).thenReturn(merchantId);
        when(requestHasher.hash(request)).thenReturn("request-hash");
        when(idempotencyService.claim(merchantId, idempotencyKey, "request-hash"))
                .thenReturn(ClaimResult.reclaimed(42L, ownerId));
        when(paymentService.findByMerchantAndIdempotencyKey(merchantId, idempotencyKey))
                .thenReturn(Optional.of(existingPayment));
        when(attemptPersistenceService.currentProviderCode(existingPayment))
                .thenReturn("PROVIDER_A");

        ResponseEntity<PaymentResponse> response = controller.createPayment(
                request, idempotencyKey, jwtFor(merchantId));

        assertEquals(202, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(existingPayment.getPaymentId(), response.getBody().paymentId());
        assertEquals("ROUTED", response.getBody().status());

        verify(orchestrationService, never()).process(any());
        verify(idempotencyService).complete(
                eq(42L),
                eq(ownerId),
                eq(existingPayment.getPaymentId()),
                any(StoredResponse.class)
        );
    }

    @Test
    void reclaimedClaimWithoutExistingPaymentStartsNormalPaymentFlow() {
        String merchantId = "merchant-1";
        String idempotencyKey = "retry-key";
        String ownerId = "recovery-owner";

        CreatePaymentRequest request = new CreatePaymentRequest(
                "order-123", 2500L, "INR", "token-abc");
        Payment newPayment = payment(PaymentStatus.SUCCESS);

        when(merchantIdentity.requireMerchantId(any())).thenReturn(merchantId);
        when(requestHasher.hash(request)).thenReturn("request-hash");
        when(idempotencyService.claim(merchantId, idempotencyKey, "request-hash"))
                .thenReturn(ClaimResult.reclaimed(42L, ownerId));
        when(paymentService.findByMerchantAndIdempotencyKey(merchantId, idempotencyKey))
                .thenReturn(Optional.empty());
        when(orchestrationService.process(any())).thenReturn(newPayment);
        when(attemptPersistenceService.currentProviderCode(newPayment))
                .thenReturn("PROVIDER_B");

        ResponseEntity<PaymentResponse> response = controller.createPayment(
                request, idempotencyKey, jwtFor(merchantId));

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(newPayment.getPaymentId(), response.getBody().paymentId());
        assertEquals("PROVIDER_B", response.getBody().provider());

        verify(orchestrationService).process(any());
        verify(idempotencyService).complete(
                eq(42L),
                eq(ownerId),
                eq(newPayment.getPaymentId()),
                any(StoredResponse.class)
        );
    }

    private Payment payment(PaymentStatus status) {
        return Payment.builder()
                .paymentId("payment-123")
                .merchantId("merchant-1")
                .merchantRef("order-123")
                .amountMinor(2500L)
                .currency("INR")
                .paymentToken("token-abc")
                .idempotencyKey("retry-key")
                .providerOperationId("provider-operation-123")
                .status(status)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Jwt jwtFor(String merchantId) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("merchant_id", merchantId)
                .build();
    }
}
