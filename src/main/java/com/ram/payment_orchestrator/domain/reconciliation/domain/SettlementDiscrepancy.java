package com.ram.payment_orchestrator.domain.reconciliation.domain;

public enum SettlementDiscrepancy {
    NONE,
    MISSING_SETTLEMENT,
    UNMATCHED_SETTLEMENT,
    AMOUNT_MISMATCH
}
