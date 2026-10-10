package com.ram.payment_orchestrator.domain.ledger.persistence;

import com.ram.payment_orchestrator.domain.ledger.domain.AccountType;
import com.ram.payment_orchestrator.domain.ledger.domain.EntryType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "ledger_entries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ledger_entry_payment_account_entry",
                columnNames = {"payment_id", "account_type", "entry_type"}
        )
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LedgerEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "payment_id", nullable = false, length = 36)
    private String paymentId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 32)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 16)
    private EntryType entryType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
