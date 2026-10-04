package com.ram.payment_orchestrator.domain.ledger.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerRepository extends JpaRepository<LedgerEntryEntity, Long> {
    List<LedgerEntryEntity> findByPaymentId(String paymentId);
}
