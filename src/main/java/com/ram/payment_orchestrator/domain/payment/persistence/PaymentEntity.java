package com.ram.payment_orchestrator.domain.payment.persistence;

import com.ram.payment_orchestrator.domain.payment.domain.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "payments",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_payment_merchant_idempotency",
               columnNames = {"merchant_id", "idempotency_key"}
       )
) // prevent double-charge for same merchant and idempotence key
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEntity {

    @Id
    @Column(name = "payment_id", nullable = false, unique = true, length = 36)
    private String paymentId;

    @Column(name = "merchant_id", nullable = false, length = 64)
    private String merchantId;

    @Column(name = "merchant_ref", nullable = false, length = 64)
    private String merchantRef;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "payment_token", nullable = false, length = 255)
    private String paymentToken;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "provider_operation_id", length = 128)
    private String providerOperationId; // Durable bridge across timeout/crash

    @Column(name = "resolver_owner", length = 64)
    private String resolverOwner;       // Exclusivity lock for background job

    @Column(name = "resolver_lease_until")
    private Instant resolverLeaseUntil; // Exclusivity lock expiry

    @Column(name = "resolution_attempts", nullable = false)
    private int resolutionAttempts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
