package com.ram.payment_orchestrator.domain.ledger.service;

import com.ram.payment_orchestrator.domain.ledger.domain.AccountType;
import com.ram.payment_orchestrator.domain.ledger.domain.EntryType;
import com.ram.payment_orchestrator.domain.ledger.domain.LedgerEntry;
import com.ram.payment_orchestrator.domain.ledger.persistence.LedgerEntryEntity;
import com.ram.payment_orchestrator.domain.ledger.persistence.LedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerRepository ledgerRepository;

    @Transactional  // ensures atomicity
    public void recordCapture(String paymentId, long amountMinor) {
        LedgerEntry debitProvider = LedgerEntry.newEntry(
                paymentId, amountMinor, AccountType.PROVIDER_CLEARING, EntryType.DEBIT);

        LedgerEntry creditMerchant = LedgerEntry.newEntry(
                paymentId, amountMinor, AccountType.MERCHANT_BALANCE, EntryType.CREDIT);

        saveAndValidate(List.of(debitProvider, creditMerchant)); // bundled both entries to list and send
    }

    @Transactional
    public void recordRefund(String paymentId, long amountMinor) {
        LedgerEntry debitMerchant = LedgerEntry.newEntry(
                paymentId, amountMinor, AccountType.MERCHANT_BALANCE, EntryType.DEBIT);

        LedgerEntry creditProvider = LedgerEntry.newEntry(
                paymentId, amountMinor, AccountType.PROVIDER_CLEARING, EntryType.CREDIT);

        saveAndValidate(List.of(debitMerchant, creditProvider));
    }

    @Transactional(readOnly = true)
    public boolean hasRefund(String paymentId) {
        List<LedgerEntryEntity> entries = ledgerRepository.findByPaymentId(paymentId);
        boolean merchantDebit = entries.stream().anyMatch(entry ->
                entry.getAccountType() == AccountType.MERCHANT_BALANCE
                        && entry.getEntryType() == EntryType.DEBIT);
        boolean providerCredit = entries.stream().anyMatch(entry ->
                entry.getAccountType() == AccountType.PROVIDER_CLEARING
                        && entry.getEntryType() == EntryType.CREDIT);
        return merchantDebit && providerCredit;
    }

    private void saveAndValidate(List<LedgerEntry> entries) {
        long totalBalance = 0;

        for (LedgerEntry entry : entries) {
            if (entry.entryType() == EntryType.DEBIT) {
                totalBalance += entry.amountMinor();
            } else {
                totalBalance -= entry.amountMinor();
            }
        }

        if (totalBalance != 0) {  // zero-sum validation
            throw new IllegalStateException("Ledger entries must be balanced (zero-sum). Current imbalance: " + totalBalance);
        }

        List<LedgerEntryEntity> entities = entries.stream()
                .map(entry -> mapToEntity(entry))
                .toList();

        ledgerRepository.saveAll(entities);
    }

    private LedgerEntryEntity mapToEntity(LedgerEntry domain) {
        return LedgerEntryEntity.builder()
                .paymentId(domain.paymentId())
                .amountMinor(domain.amountMinor())
                .accountType(domain.accountType())
                .entryType(domain.entryType())
                .createdAt(domain.createdAt())
                .build();
    }
}
