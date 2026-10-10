package com.ram.payment_orchestrator.api.payment;

import com.ram.payment_orchestrator.domain.idempotency.domain.ClaimResult;
import com.ram.payment_orchestrator.domain.idempotency.domain.StoredResponse;
import com.ram.payment_orchestrator.domain.idempotency.service.IdempotencyService;
import com.ram.payment_orchestrator.domain.payment.domain.Payment;
import com.ram.payment_orchestrator.domain.payment.domain.PaymentRequest;
import com.ram.payment_orchestrator.domain.payment.domain.PaymentStatus;
import com.ram.payment_orchestrator.domain.payment.service.PaymentOrchestrationService;
import com.ram.payment_orchestrator.domain.payment.service.PaymentService;
import com.ram.payment_orchestrator.security.MerchantIdentity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private static final String PROVIDER_CODE = "PROVIDER_A";

    private final MerchantIdentity merchantIdentity;
    private final PaymentRequestHasher requestHasher;
    private final IdempotencyService idempotencyService;
    private final PaymentOrchestrationService orchestrationService;
    private final PaymentService paymentService;
    private final JsonMapper jsonMapper;

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {

        if (idempotencyKey.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Idempotency-Key must not be blank");
        }

        String merchantId = merchantIdentity.requireMerchantId(jwt);
        String requestHash = requestHasher.hash(request);
        ClaimResult claim = idempotencyService.claim(merchantId, idempotencyKey, requestHash);

        return switch (claim.status()) {
            case REPLAY -> replay(claim.storedResponse());
            case CONFLICT -> ResponseEntity.status(CONFLICT).<PaymentResponse>build();
            case ERROR -> ResponseEntity.status(INTERNAL_SERVER_ERROR).<PaymentResponse>build();
            case SUCCESS -> claim.reclaimed()
                    ? recoverClaimedRequest(request, merchantId, idempotencyKey, claim)
                    : processClaimedRequest(request, merchantId, idempotencyKey, claim);
        };
    }

    private ResponseEntity<PaymentResponse> replay(Optional<StoredResponse> storedResponse) {
        StoredResponse stored = storedResponse.orElseThrow(() ->
                new IllegalStateException("Replay claim has no stored response"));

        try {
            PaymentResponse response = jsonMapper.readValue(stored.body(), PaymentResponse.class);
            return ResponseEntity.ok(response);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored payment response is invalid JSON", exception);
        }
    }

    private ResponseEntity<PaymentResponse> processClaimedRequest(
            CreatePaymentRequest request,
            String merchantId,
            String idempotencyKey,
            ClaimResult claim) {

        PaymentRequest paymentRequest = new PaymentRequest(
                merchantId,
                request.paymentToken(),
                request.amountMinor(),
                request.currency(),
                request.merchantRef(),
                idempotencyKey
        );

        Payment payment = orchestrationService.process(paymentRequest);
        return completeClaim(payment, claim);
    }

    private ResponseEntity<PaymentResponse> recoverClaimedRequest(
            CreatePaymentRequest request,
            String merchantId,
            String idempotencyKey,
            ClaimResult claim) {

        Optional<Payment> existingPayment = paymentService
                .findByMerchantAndIdempotencyKey(merchantId, idempotencyKey);

        if (existingPayment.isPresent()) {
            // A prior request already created the durable payment/provider operation.
            // Reuse it; never send another charge for this idempotency key.
            return completeClaim(existingPayment.get(), claim);
        }

        return processClaimedRequest(request, merchantId, idempotencyKey, claim);
    }

    private ResponseEntity<PaymentResponse> completeClaim(Payment payment, ClaimResult claim) {
        PaymentResponse response = new PaymentResponse(
                payment.getPaymentId(),
                payment.getStatus().name(),
                payment.getAmountMinor(),
                payment.getCurrency(),
                PROVIDER_CODE
        );

        int responseStatus = isPending(payment.getStatus())
                ? ACCEPTED.value()
                : CREATED.value();

        try {
            String snapshot = jsonMapper.writeValueAsString(response);
            idempotencyService.complete(
                    claim.idempotencyId(),
                    claim.ownerId(),
                    payment.getPaymentId(),
                    new StoredResponse(responseStatus, snapshot)
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not store payment response", exception);
        }

        return ResponseEntity.status(responseStatus).body(response);
    }

    private boolean isPending(PaymentStatus status) {
        return status == PaymentStatus.INITIATED
                || status == PaymentStatus.ROUTED
                || status == PaymentStatus.UNKNOWN
                || status == PaymentStatus.RESOLVING;
    }
}
