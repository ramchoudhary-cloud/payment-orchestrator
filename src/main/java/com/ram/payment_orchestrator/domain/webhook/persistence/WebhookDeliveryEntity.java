package com.ram.payment_orchestrator.domain.webhook.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "webhook_deliveries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_webhook_event",
                columnNames = "event_id"
        ),
        indexes = @Index(
                name = "idx_webhook_claim",
                columnList = "status, next_attempt_at, locked_until"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDeliveryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, length = 36)
    private String paymentId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "endpoint_url", nullable = false, length = 2048)
    private String endpointUrl;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private WebhookDeliveryStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "locked_by", length = 64)
    private String lockedBy;

    @Column(name = "locked_until")
    private Instant lockedUntil;
}
