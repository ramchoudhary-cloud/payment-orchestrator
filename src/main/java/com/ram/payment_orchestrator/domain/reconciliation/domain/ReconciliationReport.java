package com.ram.payment_orchestrator.domain.reconciliation.domain;

import java.util.List;

public record ReconciliationReport(
        int matchedCount,
        int missingSettlementCount,
        int unmatchedSettlementCount,
        int amountMismatchCount,
        List<ReconciliationLine> lines
) {
    public ReconciliationReport {
        lines = List.copyOf(lines);
    }
}
