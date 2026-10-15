package com.ram.payment_orchestrator.domain.provider.adapter;

import com.ram.payment_orchestrator.domain.provider.domain.ChargeRequest;
import com.ram.payment_orchestrator.domain.provider.domain.ChargeResult;
import com.ram.payment_orchestrator.domain.provider.domain.Outcome;
import com.ram.payment_orchestrator.domain.provider.domain.ProviderStatus;
import com.ram.payment_orchestrator.domain.provider.domain.StatusResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderBStubTests {

    private static final String OPERATION_ID = "operation-b-123";

    @Test
    void acceptsChargeAndReturnsItsStatusForTheSameOperation() {
        ProviderBStub provider = provider(0, 1000, false, false);

        ChargeResult result = provider.charge(chargeRequest());
        StatusResult status = provider.queryStatus(OPERATION_ID, result.providerRef());

        assertEquals("PROVIDER_B", provider.providerCode());
        assertEquals(Outcome.ACCEPTED, result.outcome());
        assertEquals(ProviderStatus.CHARGED, result.finalStatus());
        assertEquals("pb_" + OPERATION_ID, result.providerRef());
        assertEquals(ProviderStatus.CHARGED, status.status());
    }

    @Test
    void reportsUnavailableWithoutCharging() {
        ProviderBStub provider = provider(0, 1000, true, false);

        ChargeResult result = provider.charge(chargeRequest());
        StatusResult status = provider.queryStatus(OPERATION_ID, null);

        assertEquals(Outcome.UNREACHABLE, result.outcome());
        assertEquals(ProviderStatus.UNKNOWN, status.status());
    }

    @Test
    void timeoutCanRepresentAChargeThatMustBeResolvedByStatusQuery() {
        ProviderBStub provider = provider(2, 1, false, true);

        ChargeResult result = provider.charge(chargeRequest());
        StatusResult status = provider.queryStatus(OPERATION_ID, null);

        assertEquals(Outcome.TIMEOUT, result.outcome());
        assertEquals(ProviderStatus.CHARGED, status.status());
        assertEquals("pb_" + OPERATION_ID, status.providerRef());
    }

    @Test
    void timeoutWithoutProviderSideChargeRemainsUnknown() {
        ProviderBStub provider = provider(2, 1, false, false);

        ChargeResult result = provider.charge(chargeRequest());
        StatusResult status = provider.queryStatus(OPERATION_ID, null);

        assertEquals(Outcome.TIMEOUT, result.outcome());
        assertEquals(ProviderStatus.UNKNOWN, status.status());
    }

    private ProviderBStub provider(
            long latencyMs,
            long timeoutMs,
            boolean unavailable,
            boolean chargeOnTimeout
    ) {
        return new ProviderBStub(latencyMs, timeoutMs, unavailable, chargeOnTimeout);
    }

    private ChargeRequest chargeRequest() {
        return new ChargeRequest(
                2500L,
                "INR",
                "test-token",
                "provider-b-test-order",
                OPERATION_ID
        );
    }
}
