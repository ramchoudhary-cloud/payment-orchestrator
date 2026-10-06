package com.ram.payment_orchestrator.domain.ledger.domain;

import java.time.Instant;

public record LedgerEntry(     // java record fields are implicitly private and final
        Long id,                     // DB primary key (generated)
        String paymentId,            // Foreign key to payment
        long amountMinor,
        AccountType accountType,
        EntryType entryType,
        Instant createdAt            // Timestamp of entry creation
) {
    // Convenience factory for creating new entries (id will be set by DB)
    public static LedgerEntry newEntry(String paymentId, long amountMinor,
                                       AccountType accountType, EntryType entryType) {
        return new LedgerEntry(null, paymentId, amountMinor, accountType, entryType, Instant.now());
    }
}
