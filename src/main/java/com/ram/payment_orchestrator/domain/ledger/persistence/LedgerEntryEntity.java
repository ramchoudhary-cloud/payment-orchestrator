package com.ram.payment_orchestrator.domain.ledger.persistence;

import com.ram.payment_orchestrator.domain.ledger.model.AccountType;
import com.ram.payment_orchestrator.domain.ledger.model.EntryType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ledger_entries", 
     uniqueConstraints = @UniqueConstraint(columnNames = {"paymentId", "accountType", "entryType"})
) // handles duplicate LedgerEntry
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LedgerEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String paymentId;  // foreign key

    @Column(nullable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryType entryType;

    @Column(nullable = false)
    private Instant createdAt;
}
