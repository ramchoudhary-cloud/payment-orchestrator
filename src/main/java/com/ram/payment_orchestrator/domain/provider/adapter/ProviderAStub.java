package com.ram.payment_orchestrator.domain.provider.adapter;

import com.ram.payment_orchestrator.domain.provider.domain.ChargeRequest;
import com.ram.payment_orchestrator.domain.provider.domain.ChargeResult;
import com.ram.payment_orchestrator.domain.provider.domain.Outcome;
import com.ram.payment_orchestrator.domain.provider.domain.ProviderStatus;
import com.ram.payment_orchestrator.domain.provider.domain.StatusResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProviderAStub implements PaymentProviderAdapter {

    private final Map<String, StatusResult> operations = new ConcurrentHashMap<>();
    private final long latencyMs;
    private final long timeoutMs;
    private final boolean unavailable;
    private final boolean chargeOnTimeout;

    public ProviderAStub(
            @Value("${providers.a.latency-ms:0}") long latencyMs,
            @Value("${providers.a.timeout-ms:10000}") long timeoutMs,
            @Value("${providers.a.unavailable:false}") boolean unavailable,
            @Value("${providers.a.charge-on-timeout:false}") boolean chargeOnTimeout) {
        this.latencyMs = Math.max(0, latencyMs);
        this.timeoutMs = Math.max(0, timeoutMs);
        this.unavailable = unavailable;
        this.chargeOnTimeout = chargeOnTimeout;
    }

    @Override
    public String providerCode() {
        return "PROVIDER_A";
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        if (unavailable) {
            return new ChargeResult(Outcome.UNREACHABLE, ProviderStatus.UNKNOWN, null);
        }

        delay();
        if (latencyMs > timeoutMs) {
            if (chargeOnTimeout) {
                String providerRef = "pa_" + request.providerOperationId();
                operations.put(request.providerOperationId(),
                        new StatusResult(ProviderStatus.CHARGED, providerRef));
            }
            return new ChargeResult(Outcome.TIMEOUT, ProviderStatus.UNKNOWN, null);
        }

        String providerRef = "pa_" + request.providerOperationId();
        operations.put(request.providerOperationId(),
                new StatusResult(ProviderStatus.CHARGED, providerRef));
        return new ChargeResult(Outcome.ACCEPTED, ProviderStatus.CHARGED, providerRef);
    }

    @Override
    public StatusResult queryStatus(String providerOperationId, String providerRef) {
        return operations.getOrDefault(providerOperationId,
                new StatusResult(ProviderStatus.UNKNOWN, providerRef));
    }

    private void delay() {
        if (latencyMs == 0) {
            return;
        }
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Provider A stub call interrupted", exception);
        }
    }
}
