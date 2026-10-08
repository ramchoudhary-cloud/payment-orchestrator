package com.ram.payment_orchestrator.domain.provider.domain;

public enum Outcome {
    ACCEPTED,
    DECLINED,
    REJECTED,
    UNREACHABLE,
    TIMEOUT,
    AMBIGUOUS
}
