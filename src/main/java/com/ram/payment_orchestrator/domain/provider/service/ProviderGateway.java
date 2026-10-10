package com.ram.payment_orchestrator.domain.provider.service;

import com.ram.payment_orchestrator.domain.provider.adapter.PaymentProviderAdapter;
import com.ram.payment_orchestrator.domain.provider.domain.ChargeRequest;
import com.ram.payment_orchestrator.domain.provider.domain.ChargeResult;
import com.ram.payment_orchestrator.domain.provider.domain.Outcome;
import com.ram.payment_orchestrator.domain.provider.domain.ProviderStatus;
import com.ram.payment_orchestrator.domain.provider.domain.StatusResult;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProviderGateway {

    private final Map<String, PaymentProviderAdapter> adapters;
    private final ProviderHealthTracker healthTracker;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public ProviderGateway(
            List<PaymentProviderAdapter> adapters,
            ProviderHealthTracker healthTracker,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this.adapters = adapters.stream()
                .collect(Collectors.toUnmodifiableMap(
                        PaymentProviderAdapter::providerCode,
                        Function.identity()));
        this.healthTracker = healthTracker;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    public ChargeResult charge(String provider, ChargeRequest request) {
        PaymentProviderAdapter adapter = adapterFor(provider);
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(provider);
        if (!circuitBreaker.tryAcquirePermission()) {
            return new ChargeResult(Outcome.UNREACHABLE, ProviderStatus.UNKNOWN, null);
        }

        long startedAt = System.nanoTime();
        ChargeResult result;

        try {
            result = adapter.charge(request);
            if (result == null) {
                result = new ChargeResult(Outcome.AMBIGUOUS, ProviderStatus.UNKNOWN, null);
            }
        } catch (RuntimeException exception) {
            // The gateway cannot know whether the provider received the request.
            result = new ChargeResult(Outcome.AMBIGUOUS, ProviderStatus.UNKNOWN, null);
            circuitBreaker.onError(
                    System.nanoTime() - startedAt,
                    TimeUnit.NANOSECONDS,
                    exception
            );
            recordHealthOutcome(provider, result.outcome());
            return result;
        }

        if (isTechnicalFailure(result.outcome())) {
            circuitBreaker.onError(
                    System.nanoTime() - startedAt,
                    TimeUnit.NANOSECONDS,
                    new IllegalStateException("Provider technical failure: " + result.outcome())
            );
        } else {
            circuitBreaker.onSuccess(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
        }

        recordHealthOutcome(provider, result.outcome());
        return result;
    }

    public boolean isCircuitOpen(String provider) {
        return circuitBreakerRegistry.circuitBreaker(provider).getState() == CircuitBreaker.State.OPEN;
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

    private boolean isTechnicalFailure(Outcome outcome) {
        return outcome == null
                || outcome == Outcome.TIMEOUT
                || outcome == Outcome.AMBIGUOUS
                || outcome == Outcome.UNREACHABLE;
    }

    private void recordHealthOutcome(String provider, Outcome outcome) {
        try {
            healthTracker.recordOutcome(provider, outcome);
        } catch (RuntimeException exception) {
            // Health tracking must not prevent payment finalization.
            log.warn("Could not record health outcome for provider {}", provider, exception);
        }
    }
}
