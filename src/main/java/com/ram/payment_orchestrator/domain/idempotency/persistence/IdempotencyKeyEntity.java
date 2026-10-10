package com.ram.payment_orchestrator.domain.idempotency.persistence;

import com.ram.payment_orchestrator.domain.idempotency.domain.IdempotencyStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_idempotency",
               columnNames = {"merchant_id", "idempotency_key"}
       )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false, length = 64)
    private String merchantId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "payment_id", length = 36)
    private String paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IdempotencyStatus status;

    @Column(name = "response_snapshot", columnDefinition = "TEXT")
    private String responseSnapshot; // Stored JSON response

    @Column(name = "response_status")
    private Integer responseStatus;   // HTTP status code (e.g., 201)

    @Column(name = "owner_id", length = 64)
    private String ownerId;           // Identity of the worker claiming this request

    @Column(name = "lease_until")
    private Instant leaseUntil;       // When the claim expires (for crash recovery)

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
