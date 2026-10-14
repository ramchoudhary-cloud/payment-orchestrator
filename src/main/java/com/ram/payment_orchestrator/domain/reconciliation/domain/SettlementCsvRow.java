package com.ram.payment_orchestrator.domain.reconciliation.domain;

import java.time.LocalDate;

public record SettlementCsvRow(
        String providerCode,
        String providerRef,
        long amountMinor,
        LocalDate settlementDate
) {
}
