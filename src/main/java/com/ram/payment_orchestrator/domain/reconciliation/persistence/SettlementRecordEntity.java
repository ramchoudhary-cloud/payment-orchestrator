package com.ram.payment_orchestrator.domain.reconciliation.persistence;

import com.ram.payment_orchestrator.domain.reconciliation.domain.SettlementMatchStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
        name = "settlement_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_settlement_provider_ref_date",
                columnNames = {"provider_code", "provider_ref", "settlement_date"}
        ),
        indexes = @Index(
                name = "idx_settlement_provider_date",
                columnList = "provider_code, settlement_date"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_code", nullable = false, length = 32)
    private String providerCode;

    @Column(name = "provider_ref", nullable = false, length = 128)
    private String providerRef;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 32)
    private SettlementMatchStatus matchStatus;
}
