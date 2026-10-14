package com.ram.payment_orchestrator.domain.reconciliation.domain;

public enum SettlementMatchStatus {
    MATCHED,
    MISSING_SETTLEMENT,
    UNMATCHED_SETTLEMENT,
    AMOUNT_MISMATCH
}
