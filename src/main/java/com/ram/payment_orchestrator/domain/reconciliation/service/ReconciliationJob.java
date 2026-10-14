package com.ram.payment_orchestrator.domain.reconciliation.service;

import com.ram.payment_orchestrator.domain.reconciliation.domain.*;
import com.ram.payment_orchestrator.domain.reconciliation.persistence.SettlementRecordRepository;
import com.ram.payment_orchestrator.domain.payment.persistence.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReconciliationJob {

    private final SettlementCsvReader csvReader;
    private final PaymentRepository paymentRepository;
    private final SettlementRecordRepository settlementRepository;

    @Transactional
    public ReconciliationReport reconcile(String csv) {
        List<SettlementCsvRow> settlements = csvReader.read(csv);
        Map<RecordKey, InternalSettlementRecord> internalByKey = new HashMap<>();
        List<InternalSettlementRecord> internalWithoutProviderRef = new ArrayList<>();

        for (InternalSettlementRecord internal : paymentRepository.findSuccessfulPaymentsForReconciliation()) {
            if (internal.getProviderRef() == null || internal.getProviderRef().isBlank()) {
                internalWithoutProviderRef.add(internal);
                continue;
            }
            RecordKey key = new RecordKey(internal.getProviderCode(), internal.getProviderRef());
            if (internalByKey.putIfAbsent(key, internal) != null) {
                throw new IllegalStateException("Internal successful payments have duplicate provider references: "
                        + key.providerCode() + "/" + key.providerRef());
            }
        }

        Set<RecordKey> seenSettlementKeys = new HashSet<>();
        Set<RecordKey> matchedKeys = new HashSet<>();
        List<ReconciliationLine> lines = new ArrayList<>();
        List<SettlementCsvRow> rowsToPersist = new ArrayList<>();

        for (SettlementCsvRow settlement : settlements) {
            RecordKey key = new RecordKey(settlement.providerCode(), settlement.providerRef());
            if (!seenSettlementKeys.add(key)) {
                throw new IllegalArgumentException("Settlement CSV contains duplicate provider references: "
                        + key.providerCode() + "/" + key.providerRef());
            }

            InternalSettlementRecord internal = internalByKey.get(key);
            SettlementMatchStatus status;
            Long expectedAmount = null;
            if (internal == null) {
                status = SettlementMatchStatus.UNMATCHED_SETTLEMENT;
            } else {
                expectedAmount = internal.getAmountMinor();
                matchedKeys.add(key);
                status = expectedAmount == settlement.amountMinor()
                        ? SettlementMatchStatus.MATCHED
                        : SettlementMatchStatus.AMOUNT_MISMATCH;
            }

            lines.add(new ReconciliationLine(
                    settlement.providerCode(), settlement.providerRef(), expectedAmount,
                    settlement.amountMinor(), settlement.settlementDate(), status
            ));
            rowsToPersist.add(settlement);
        }

        for (Map.Entry<RecordKey, InternalSettlementRecord> entry : internalByKey.entrySet()) {
            if (!matchedKeys.contains(entry.getKey())) {
                InternalSettlementRecord internal = entry.getValue();
                lines.add(new ReconciliationLine(
                        internal.getProviderCode(), internal.getProviderRef(), internal.getAmountMinor(),
                        null, null, SettlementMatchStatus.MISSING_SETTLEMENT
                ));
            }
        }
        for (InternalSettlementRecord internal : internalWithoutProviderRef) {
            lines.add(new ReconciliationLine(
                    internal.getProviderCode(), null, internal.getAmountMinor(), null,
                    null, SettlementMatchStatus.MISSING_SETTLEMENT
            ));
        }

        rowsToPersist.forEach(row -> settlementRepository.insertOrUpdate(
                row.providerCode(), row.providerRef(), row.amountMinor(), row.settlementDate(),
                statusFor(row, internalByKey).name()
        ));

        return report(lines);
    }

    private SettlementMatchStatus statusFor(
            SettlementCsvRow settlement,
            Map<RecordKey, InternalSettlementRecord> internalByKey
    ) {
        InternalSettlementRecord internal = internalByKey.get(
                new RecordKey(settlement.providerCode(), settlement.providerRef())
        );
        if (internal == null) {
            return SettlementMatchStatus.UNMATCHED_SETTLEMENT;
        }
        return internal.getAmountMinor() == settlement.amountMinor()
                ? SettlementMatchStatus.MATCHED
                : SettlementMatchStatus.AMOUNT_MISMATCH;
    }

    private ReconciliationReport report(List<ReconciliationLine> lines) {
        return new ReconciliationReport(
                count(lines, SettlementMatchStatus.MATCHED),
                count(lines, SettlementMatchStatus.MISSING_SETTLEMENT),
                count(lines, SettlementMatchStatus.UNMATCHED_SETTLEMENT),
                count(lines, SettlementMatchStatus.AMOUNT_MISMATCH),
                lines
        );
    }

    private int count(List<ReconciliationLine> lines, SettlementMatchStatus status) {
        return (int) lines.stream().filter(line -> line.matchStatus() == status).count();
    }

    private record RecordKey(String providerCode, String providerRef) {
    }
}
