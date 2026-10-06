package com.ram.payment_orchestrator.domain.payment.persistence;

import com.ram.payment_orchestrator.domain.payment.domain.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "payments", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"merchantId", "idempotencyKey"})
) // prevent double-charge for same merchant and idempotence key
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEntity {

    @Id
    @Column(nullable = false, unique = true)
    private String paymentId;

    @Column(nullable = false)
    private String merchantId;

    @Column(nullable = false)
    private String merchantRef;

    @Column(nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private String paymentToken;

    @Column(nullable = false)
    private String idempotencyKey;

    private String providerOperationId; // Durable bridge across timeout/crash

    private String resolverOwner;       // Exclusivity lock for background job

    private Instant resolverLeaseUntil; // Exclusivity lock expiry

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
