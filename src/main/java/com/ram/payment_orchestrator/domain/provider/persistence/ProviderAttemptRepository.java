package com.ram.payment_orchestrator.domain.provider.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderAttemptRepository extends JpaRepository<ProviderAttemptEntity, Long> {

    List<ProviderAttemptEntity> findByPaymentIdOrderByCreatedAtDesc(String paymentId);

    Optional<ProviderAttemptEntity> findByProviderCodeAndProviderOperationId(
            String providerCode,
            String providerOperationId
    );

    Optional<ProviderAttemptEntity> findByPaymentIdAndProviderOperationId(
            String paymentId,
            String providerOperationId
    );

    boolean existsByPaymentIdAndProviderCode(String paymentId, String providerCode);
}
