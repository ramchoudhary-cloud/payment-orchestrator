package com.ram.payment_orchestrator.domain.ledger.service;

import com.ram.payment_orchestrator.domain.ledger.domain.AccountType;
import com.ram.payment_orchestrator.domain.ledger.domain.EntryType;
import com.ram.payment_orchestrator.domain.ledger.persistence.LedgerEntryEntity;
import com.ram.payment_orchestrator.domain.ledger.persistence.LedgerRepository;
import com.ram.payment_orchestrator.domain.payment.domain.PaymentStatus;
import com.ram.payment_orchestrator.domain.payment.persistence.PaymentEntity;
import com.ram.payment_orchestrator.domain.payment.persistence.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(LedgerService.class)
class LedgerServicePersistenceTests {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private LedgerRepository ledgerRepository;

    @Autowired
    private LedgerService ledgerService;

    @Test
    void recordCapturePersistsBalancedDebitAndCreditEntries() {
        String paymentId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        paymentRepository.saveAndFlush(PaymentEntity.builder()
                .paymentId(paymentId)
                .merchantId("test-merchant")
                .merchantRef("test-order")
                .amountMinor(2500L)
                .currency("INR")
                .paymentToken("test-token")
                .idempotencyKey(UUID.randomUUID().toString())
                .status(PaymentStatus.SUCCESS)
                .resolutionAttempts(0)
                .createdAt(now)
                .updatedAt(now)
                .build());

        ledgerService.recordCapture(paymentId, 2500L);

        List<LedgerEntryEntity> entries = ledgerRepository.findByPaymentId(paymentId);

        assertEquals(2, entries.size());
        assertEquals(1, entries.stream()
                .filter(entry -> entry.getEntryType() == EntryType.DEBIT)
                .count());
        assertEquals(1, entries.stream()
                .filter(entry -> entry.getEntryType() == EntryType.CREDIT)
                .count());
        assertEquals(1, entries.stream()
                .filter(entry -> entry.getAccountType() == AccountType.PROVIDER_CLEARING)
                .count());
        assertEquals(1, entries.stream()
                .filter(entry -> entry.getAccountType() == AccountType.MERCHANT_BALANCE)
                .count());

        long signedTotal = entries.stream()
                .mapToLong(entry -> entry.getEntryType() == EntryType.DEBIT
                        ? entry.getAmountMinor()
                        : -entry.getAmountMinor())
                .sum();

        assertEquals(0L, signedTotal);
    }
}
