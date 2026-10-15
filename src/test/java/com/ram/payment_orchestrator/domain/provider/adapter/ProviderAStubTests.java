package com.ram.payment_orchestrator.domain.provider.adapter;

import com.ram.payment_orchestrator.domain.provider.domain.ChargeRequest;
import com.ram.payment_orchestrator.domain.provider.domain.Outcome;
import com.ram.payment_orchestrator.domain.provider.domain.ProviderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderAStubTests {

    @Test
    void configuredOutageBeginsAfterHealthyChargeCalls() {
        ProviderAStub provider = new ProviderAStub(0, 10_000, false, false, 1);

        var healthy = provider.charge(request("operation-1"));
        var unavailable = provider.charge(request("operation-2"));

        assertEquals(Outcome.ACCEPTED, healthy.outcome());
        assertEquals(ProviderStatus.CHARGED, healthy.finalStatus());
        assertEquals(Outcome.UNREACHABLE, unavailable.outcome());
        assertEquals(ProviderStatus.UNKNOWN, unavailable.finalStatus());
        assertEquals(ProviderStatus.CHARGED,
                provider.queryStatus("operation-1", healthy.providerRef()).status());
        assertEquals(ProviderStatus.UNKNOWN,
                provider.queryStatus("operation-2", unavailable.providerRef()).status());
    }

    private ChargeRequest request(String operationId) {
        return new ChargeRequest(2500L, "INR", "test-token", "test-order", operationId);
    }
}
