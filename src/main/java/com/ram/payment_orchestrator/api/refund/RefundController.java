package com.ram.payment_orchestrator.api.refund;

import com.ram.payment_orchestrator.api.payment.PaymentRequestHasher;
import com.ram.payment_orchestrator.domain.idempotency.domain.ClaimResult;
import com.ram.payment_orchestrator.domain.idempotency.domain.StoredResponse;
import com.ram.payment_orchestrator.domain.idempotency.service.IdempotencyService;
import com.ram.payment_orchestrator.domain.payment.service.PaymentOrchestrationService;
import com.ram.payment_orchestrator.domain.payment.domain.RefundException;
import com.ram.payment_orchestrator.security.MerchantIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@RestController
@RequestMapping("/api/v1/payments/{paymentId}/refund")
@RequiredArgsConstructor
public class RefundController {

    private final MerchantIdentity merchantIdentity;
    private final PaymentRequestHasher requestHasher;
    private final IdempotencyService idempotencyService;
    private final PaymentOrchestrationService orchestrationService;
    private final JsonMapper jsonMapper;

    @PostMapping
    public ResponseEntity<RefundResponse> refund(
            @PathVariable String paymentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (idempotencyKey.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Idempotency-Key must not be blank");
        }

        String merchantId = merchantIdentity.requireMerchantId(jwt);
        String requestHash = requestHasher.hashRefund(paymentId);
        ClaimResult claim = idempotencyService.claim(merchantId, idempotencyKey, requestHash);

        return switch (claim.status()) {
            case REPLAY -> replay(claim);
            case CONFLICT -> ResponseEntity.status(CONFLICT).build();
            case ERROR -> ResponseEntity.status(INTERNAL_SERVER_ERROR).build();
            case SUCCESS -> completeRefund(paymentId, merchantId, claim);
        };
    }

    private ResponseEntity<RefundResponse> replay(ClaimResult claim) {
        StoredResponse stored = claim.storedResponse().orElseThrow(() ->
                new IllegalStateException("Replay claim has no stored response"));
        try {
            RefundResponse response = jsonMapper.readValue(stored.body(), RefundResponse.class);
            return ResponseEntity.ok(response);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored refund response is invalid JSON", exception);
        }
    }

    private ResponseEntity<RefundResponse> completeRefund(
            String paymentId,
            String merchantId,
            ClaimResult claim
    ) {
        RefundResponse response = new RefundResponse(paymentId, "REFUNDED");
        try {
            StoredResponse stored = new StoredResponse(
                    CREATED.value(), jsonMapper.writeValueAsString(response));
            try {
                orchestrationService.refund(
                        merchantId, paymentId, claim.idempotencyId(), claim.ownerId(), stored);
            } catch (RefundException exception) {
                HttpStatus status = exception.reason() == RefundException.Reason.PAYMENT_NOT_FOUND
                        ? HttpStatus.NOT_FOUND
                        : HttpStatus.CONFLICT;
                throw new ResponseStatusException(status, exception.getMessage(), exception);
            }
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not store refund response", exception);
        }
        return ResponseEntity.status(CREATED).body(response);
    }
}
