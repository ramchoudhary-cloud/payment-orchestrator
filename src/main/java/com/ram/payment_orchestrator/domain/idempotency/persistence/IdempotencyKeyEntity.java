package com.ram.payment_orchestrator.domain.idempotency.persistence;

import com.ram.payment_orchestrator.domain.idempotency.domain.IdempotencyStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys",
       uniqueConstraints = @UniqueConstraint(columnNames = {"merchantId", "idempotencyKey"})
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

    @Column(nullable = false)
    private String merchantId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(nullable = false)
    private String requestHash;

    private String paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdempotencyStatus status;

    @Column(columnDefinition = "TEXT")
    private String responseSnapshot; // Stored JSON response

    private Integer responseStatus;   // HTTP status code (e.g., 201)

    private String ownerId;           // Identity of the worker claiming this request

    private Instant leaseUntil;       // When the claim expires (for crash recovery)

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
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
