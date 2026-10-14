package com.ram.payment_orchestrator.domain.provider.service;

import com.ram.payment_orchestrator.domain.provider.adapter.PaymentProviderAdapter;
import com.ram.payment_orchestrator.domain.provider.service.ProviderHealthTracker.HealthSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RoutingService {

    private final List<String> providerPriority;
    private final ProviderGateway providerGateway;
    private final ProviderHealthTracker healthTracker;

    public RoutingService(
            List<PaymentProviderAdapter> adapters,
            ProviderGateway providerGateway,
            ProviderHealthTracker healthTracker,
            @Value("${payment.routing.provider-priority:PROVIDER_A}") String configuredPriority
    ) {
        Set<String> availableProviders = adapters.stream()
                .map(PaymentProviderAdapter::providerCode)
                .collect(Collectors.toUnmodifiableSet());

        List<String> configuredProviders = List.of(configuredPriority.split(","))
                .stream()
                .map(String::trim)
                .filter(provider -> !provider.isEmpty())
                .distinct()
                .toList();

        if (configuredProviders.isEmpty()) {
            throw new IllegalStateException("At least one provider must be configured for routing");
        }
        if (!availableProviders.containsAll(configuredProviders)) {
            throw new IllegalStateException(
                    "Provider priority contains a provider without a configured adapter"
            );
        }

        this.providerPriority = configuredProviders;
        this.providerGateway = providerGateway;
        this.healthTracker = healthTracker;
    }

    public String selectProvider(Set<String> previouslyAttemptedProviders) {
        return selectNextProvider(previouslyAttemptedProviders)
                .orElseThrow(() -> new IllegalStateException(
                        "No eligible payment provider is available"
                ));
    }

    public Optional<String> selectNextProvider(Set<String> previouslyAttemptedProviders) {
        Set<String> attempted = previouslyAttemptedProviders == null
                ? Set.of()
                : previouslyAttemptedProviders;

        List<String> eligible = providerPriority.stream()
                .filter(provider -> !attempted.contains(provider))
                .filter(provider -> !providerGateway.isCircuitOpen(provider))
                .toList();

        if (eligible.isEmpty()) {
            return Optional.empty();
        }

        try {
            var health = eligible.stream()
                    .collect(Collectors.toMap(
                            provider -> provider,
                            healthTracker::getHealth
                    ));

            boolean allSamplesSufficient = eligible.stream()
                    .allMatch(provider -> health.get(provider).sampleIsSufficient());

            if (allSamplesSufficient) {
                return eligible.stream()
                        .max(Comparator
                                .comparingDouble((String provider) ->
                                        health.get(provider).technicalSuccessRate())
                                .thenComparingInt(provider -> -providerPriority.indexOf(provider)));
            }
        } catch (RuntimeException exception) {
            log.warn("Provider health unavailable; using configured provider priority", exception);
        }

        return Optional.of(eligible.get(0));
    }
}
