package com.ram.payment_orchestrator.domain.provider.service;

import com.ram.payment_orchestrator.domain.provider.adapter.PaymentProviderAdapter;
import com.ram.payment_orchestrator.domain.provider.service.ProviderHealthTracker.HealthSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoutingServiceTests {

    private ProviderGateway providerGateway;
    private ProviderHealthTracker healthTracker;
    private PaymentProviderAdapter providerA;
    private PaymentProviderAdapter providerB;

    @BeforeEach
    void setUp() {
        providerGateway = mock(ProviderGateway.class);
        healthTracker = mock(ProviderHealthTracker.class);
        providerA = adapter("PROVIDER_A");
        providerB = adapter("PROVIDER_B");

        when(providerGateway.isCircuitOpen("PROVIDER_A")).thenReturn(false);
        when(providerGateway.isCircuitOpen("PROVIDER_B")).thenReturn(false);
    }

    @Test
    void usesConfiguredPriorityWhenHealthSamplesAreInsufficient() {
        when(healthTracker.getHealth("PROVIDER_B")).thenReturn(health(0, 0, false));
        when(healthTracker.getHealth("PROVIDER_A")).thenReturn(health(0, 0, false));

        RoutingService routingService = routingService("PROVIDER_B,PROVIDER_A");

        assertEquals("PROVIDER_B", routingService.selectProvider(Set.of()));
    }

    @Test
    void skipsProvidersWithOpenCircuits() {
        when(providerGateway.isCircuitOpen("PROVIDER_B")).thenReturn(true);
        when(healthTracker.getHealth("PROVIDER_A")).thenReturn(health(0, 0, false));

        RoutingService routingService = routingService("PROVIDER_B,PROVIDER_A");

        assertEquals("PROVIDER_A", routingService.selectProvider(Set.of()));
    }

    @Test
    void skipsProvidersAlreadyAttemptedForPayment() {
        when(healthTracker.getHealth("PROVIDER_B")).thenReturn(health(0, 0, false));

        RoutingService routingService = routingService("PROVIDER_A,PROVIDER_B");

        assertEquals(
                "PROVIDER_B",
                routingService.selectProvider(Set.of("PROVIDER_A"))
        );
    }

    @Test
    void usesHighestTechnicalSuccessRateWhenEverySampleIsSufficient() {
        when(healthTracker.getHealth("PROVIDER_A")).thenReturn(health(8, 2, true));
        when(healthTracker.getHealth("PROVIDER_B")).thenReturn(health(9, 1, true));

        RoutingService routingService = routingService("PROVIDER_A,PROVIDER_B");

        assertEquals("PROVIDER_B", routingService.selectProvider(Set.of()));
    }

    @Test
    void fallsBackToPriorityWhenHealthStoreIsUnavailable() {
        when(healthTracker.getHealth("PROVIDER_B"))
                .thenThrow(new IllegalStateException("Redis unavailable"));

        RoutingService routingService = routingService("PROVIDER_B,PROVIDER_A");

        assertEquals("PROVIDER_B", routingService.selectProvider(Set.of()));
    }

    @Test
    void failsWhenNoEligibleProviderRemains() {
        when(providerGateway.isCircuitOpen("PROVIDER_A")).thenReturn(true);
        when(providerGateway.isCircuitOpen("PROVIDER_B")).thenReturn(true);

        RoutingService routingService = routingService("PROVIDER_A,PROVIDER_B");

        assertThrows(
                IllegalStateException.class,
                () -> routingService.selectProvider(Set.of())
        );
    }

    private RoutingService routingService(String priority) {
        return new RoutingService(
                List.of(providerA, providerB),
                providerGateway,
                healthTracker,
                priority
        );
    }

    private PaymentProviderAdapter adapter(String providerCode) {
        PaymentProviderAdapter adapter = mock(PaymentProviderAdapter.class);
        when(adapter.providerCode()).thenReturn(providerCode);
        return adapter;
    }

    private HealthSnapshot health(long successes, long failures, boolean sufficient) {
        long sampleSize = successes + failures;
        double successRate = sampleSize == 0 ? 0.0 : (double) successes / sampleSize;
        return new HealthSnapshot(successes, failures, sampleSize, successRate, sufficient);
    }
}
