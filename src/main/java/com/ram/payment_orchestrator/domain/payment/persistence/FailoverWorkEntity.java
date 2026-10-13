package com.ram.payment_orchestrator.domain.payment.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "payment_failover_work", uniqueConstraints = @UniqueConstraint(
        name = "uk_failover_payment_source_operation",
        columnNames = {"payment_id", "source_operation_id"}
))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FailoverWorkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, length = 36)
    private String paymentId;

    @Column(name = "source_operation_id", nullable = false, length = 128)
    private String sourceOperationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FailoverWorkStatus status;

    @Column(name = "owner_id", length = 64)
    private String ownerId;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static FailoverWorkEntity pending(String paymentId, String sourceOperationId, Instant now) {
        return FailoverWorkEntity.builder()
                .paymentId(paymentId)
                .sourceOperationId(sourceOperationId)
                .status(FailoverWorkStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
