package com.ram.payment_orchestrator.domain.reconciliation.domain;

public interface InternalSettlementRecord {
    String getProviderCode();

    String getProviderRef();

    Long getAmountMinor();
}
