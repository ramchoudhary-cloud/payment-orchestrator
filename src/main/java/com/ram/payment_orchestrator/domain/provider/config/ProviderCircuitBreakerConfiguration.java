package com.ram.payment_orchestrator.domain.provider.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ProviderCircuitBreakerConfiguration {

    @Bean
    CircuitBreakerRegistry providerCircuitBreakerRegistry(
            @Value("${payment.routing.circuit.failure-rate-threshold:50}") float failureRateThreshold,
            @Value("${payment.routing.circuit.sliding-window-size:10}") int slidingWindowSize,
            @Value("${payment.routing.circuit.minimum-number-of-calls:5}") int minimumNumberOfCalls,
            @Value("${payment.routing.circuit.open-duration-seconds:30}") long openDurationSeconds,
            @Value("${payment.routing.circuit.half-open-permitted-calls:3}") int halfOpenPermittedCalls
    ) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .waitDurationInOpenState(Duration.ofSeconds(openDurationSeconds))
                .permittedNumberOfCallsInHalfOpenState(halfOpenPermittedCalls)
                .build();

        return CircuitBreakerRegistry.of(config);
    }
}
