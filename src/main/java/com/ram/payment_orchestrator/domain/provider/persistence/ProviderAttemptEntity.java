package com.ram.payment_orchestrator.domain.provider.persistence;

import com.ram.payment_orchestrator.domain.provider.domain.Outcome;
import com.ram.payment_orchestrator.domain.provider.domain.ProviderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "provider_attempts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_provider_operation",
                columnNames = {"provider_code", "provider_operation_id"}
        ),
        indexes = @Index(
                name = "idx_payment_attempt",
                columnList = "payment_id, created_at"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, length = 36)
    private String paymentId;

    @Column(name = "provider_code", nullable = false, length = 32)
    private String providerCode;

    @Column(name = "provider_operation_id", nullable = false, length = 128)
    private String providerOperationId;

    @Column(name = "provider_ref", length = 128)
    private String providerRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 24)
    private Outcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolved_status", length = 32)
    private ProviderStatus resolvedStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
