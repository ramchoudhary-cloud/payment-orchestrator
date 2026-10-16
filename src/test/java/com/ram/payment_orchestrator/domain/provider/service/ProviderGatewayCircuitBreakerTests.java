package com.ram.payment_orchestrator.domain.provider.service;

import com.ram.payment_orchestrator.domain.provider.adapter.PaymentProviderAdapter;
import com.ram.payment_orchestrator.domain.provider.domain.ChargeRequest;
import com.ram.payment_orchestrator.domain.provider.domain.ChargeResult;
import com.ram.payment_orchestrator.domain.provider.domain.Outcome;
import com.ram.payment_orchestrator.domain.provider.domain.ProviderStatus;
import com.ram.payment_orchestrator.domain.provider.domain.StatusResult;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderGatewayCircuitBreakerTests {

    private static final String PROVIDER_CODE = "PROVIDER_A";

    private PaymentProviderAdapter providerAdapter;
    private ProviderHealthTracker healthTracker;
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private ProviderGateway providerGateway;

    @BeforeEach
    void setUp() {
        providerAdapter = mock(PaymentProviderAdapter.class);
        healthTracker = mock(ProviderHealthTracker.class);
        when(providerAdapter.providerCode()).thenReturn(PROVIDER_CODE);
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();
        circuitBreakerRegistry = CircuitBreakerRegistry.of(config);
        providerGateway = new ProviderGateway(
                List.of(providerAdapter),
                healthTracker,
                circuitBreakerRegistry
        );
    }

    @Test
    void opensCircuitAfterTechnicalFailuresAndBlocksFurtherCharges() {
        when(providerAdapter.charge(any(ChargeRequest.class)))
                .thenReturn(technicalFailure());

        assertEquals(Outcome.TIMEOUT, providerGateway.charge(PROVIDER_CODE, chargeRequest()).outcome());
        assertEquals(Outcome.TIMEOUT, providerGateway.charge(PROVIDER_CODE, chargeRequest()).outcome());

        assertTrue(providerGateway.isCircuitOpen(PROVIDER_CODE));
        ChargeResult blockedResult = providerGateway.charge(PROVIDER_CODE, chargeRequest());

        assertEquals(Outcome.UNREACHABLE, blockedResult.outcome());
        verify(providerAdapter, times(2)).charge(any(ChargeRequest.class));
        verify(healthTracker, times(2)).recordOutcome(PROVIDER_CODE, Outcome.TIMEOUT);
    }

    @Test
    void continuesStatusQueriesWhileChargeCircuitIsOpen() {
        when(providerAdapter.charge(any(ChargeRequest.class)))
                .thenReturn(technicalFailure());
        when(providerAdapter.queryStatus("operation-123", "provider-ref-123"))
                .thenReturn(new StatusResult(ProviderStatus.CHARGED, "provider-ref-123"));

        providerGateway.charge(PROVIDER_CODE, chargeRequest());
        providerGateway.charge(PROVIDER_CODE, chargeRequest());

        StatusResult status = providerGateway.queryStatus(
                PROVIDER_CODE,
                "operation-123",
                "provider-ref-123"
        );

        assertTrue(providerGateway.isCircuitOpen(PROVIDER_CODE));
        assertEquals(ProviderStatus.CHARGED, status.status());
        verify(providerAdapter).queryStatus("operation-123", "provider-ref-123");
    }

    private ChargeResult technicalFailure() {
        return new ChargeResult(Outcome.TIMEOUT, ProviderStatus.UNKNOWN, null);
    }

    private ChargeRequest chargeRequest() {
        return new ChargeRequest(
                2500L,
                "INR",
                "test-token",
                "circuit-breaker-test-order",
                "operation-123"
        );
    }
}
