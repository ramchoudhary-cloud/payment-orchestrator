package com.ram.payment_orchestrator.domain.provider.service;

import com.ram.payment_orchestrator.domain.provider.adapter.PaymentProviderAdapter;
import com.ram.payment_orchestrator.domain.provider.domain.ChargeRequest;
import com.ram.payment_orchestrator.domain.provider.domain.ChargeResult;
import com.ram.payment_orchestrator.domain.provider.domain.Outcome;
import com.ram.payment_orchestrator.domain.provider.domain.ProviderStatus;
import com.ram.payment_orchestrator.domain.provider.domain.StatusResult;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProviderGateway {

    private final Map<String, PaymentProviderAdapter> adapters;

    public ProviderGateway(List<PaymentProviderAdapter> adapters) {
        this.adapters = adapters.stream()
                .collect(Collectors.toUnmodifiableMap(
                        PaymentProviderAdapter::providerCode,
                        Function.identity()));
    }

    public ChargeResult charge(String provider, ChargeRequest request) {
        PaymentProviderAdapter adapter = adapterFor(provider);
        try {
            ChargeResult result = adapter.charge(request);
            return result == null
                    ? new ChargeResult(Outcome.AMBIGUOUS, ProviderStatus.UNKNOWN, null)
                    : result;
        } catch (RuntimeException exception) {
            // The gateway cannot know whether the provider received the request.
            return new ChargeResult(Outcome.AMBIGUOUS, ProviderStatus.UNKNOWN, null);
        }
    }

    public StatusResult queryStatus(String provider, String operationId, String providerRef) {
        PaymentProviderAdapter adapter = adapterFor(provider);
        try {
            StatusResult result = adapter.queryStatus(operationId, providerRef);
            return result == null
                    ? new StatusResult(ProviderStatus.UNKNOWN, providerRef)
                    : result;
        } catch (RuntimeException exception) {
            return new StatusResult(ProviderStatus.UNKNOWN, providerRef);
        }
    }

    private PaymentProviderAdapter adapterFor(String provider) {
        PaymentProviderAdapter adapter = adapters.get(provider);
        if (adapter == null) {
            throw new IllegalArgumentException("No provider adapter configured for: " + provider);
        }
        return adapter;
    }
}
