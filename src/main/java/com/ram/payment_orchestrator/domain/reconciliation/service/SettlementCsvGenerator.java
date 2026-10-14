package com.ram.payment_orchestrator.domain.reconciliation.service;

import com.ram.payment_orchestrator.domain.reconciliation.domain.SettlementCsvRow;
import com.ram.payment_orchestrator.domain.reconciliation.domain.SettlementDiscrepancy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SettlementCsvGenerator {

    private static final String HEADER =
            "provider_code,provider_ref,amount_minor,settlement_date\n";

    public String generate(
            List<SettlementCsvRow> sourceRows,
            SettlementDiscrepancy discrepancy
    ) {
        List<SettlementCsvRow> rows = new ArrayList<>(sourceRows);

        switch (discrepancy) {
            case NONE -> {
            }
            case MISSING_SETTLEMENT -> {
                if (!rows.isEmpty()) {
                    rows.remove(0);
                }
            }
            case UNMATCHED_SETTLEMENT -> {
                if (!rows.isEmpty()) {
                    SettlementCsvRow source = rows.get(0);
                    rows.add(new SettlementCsvRow(
                            source.providerCode(),
                            "unmatched-" + source.providerRef(),
                            source.amountMinor(),
                            source.settlementDate()
                    ));
                }
            }
            case AMOUNT_MISMATCH -> {
                if (!rows.isEmpty()) {
                    SettlementCsvRow source = rows.get(0);
                    rows.set(0, new SettlementCsvRow(
                            source.providerCode(),
                            source.providerRef(),
                            Math.addExact(source.amountMinor(), 1),
                            source.settlementDate()
                    ));
                }
            }
        }

        StringBuilder csv = new StringBuilder(HEADER);
        for (SettlementCsvRow row : rows) {
            csv.append(escape(row.providerCode())).append(',')
                    .append(escape(row.providerRef())).append(',')
                    .append(row.amountMinor()).append(',')
                    .append(row.settlementDate()).append('\n');
        }
        return csv.toString();
    }

    private String escape(String value) {
        if (value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
