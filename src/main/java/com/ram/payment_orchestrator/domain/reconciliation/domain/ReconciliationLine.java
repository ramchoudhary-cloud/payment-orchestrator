package com.ram.payment_orchestrator.domain.reconciliation.domain;

import java.time.LocalDate;

public record ReconciliationLine(
        String providerCode,
        String providerRef,
        Long expectedAmountMinor,
        Long settlementAmountMinor,
        LocalDate settlementDate,
        SettlementMatchStatus matchStatus
) {
}
