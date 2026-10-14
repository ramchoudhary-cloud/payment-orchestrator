package com.ram.payment_orchestrator.domain.reconciliation.service;

import com.ram.payment_orchestrator.domain.reconciliation.domain.SettlementCsvRow;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class SettlementCsvReader {

    private static final List<String> HEADER = List.of(
            "provider_code", "provider_ref", "amount_minor", "settlement_date"
    );

    public List<SettlementCsvRow> read(String csv) {
        List<List<String>> records = parse(csv);
        if (records.isEmpty() || !records.get(0).equals(HEADER)) {
            throw new IllegalArgumentException("Settlement CSV has an invalid or missing header");
        }

        List<SettlementCsvRow> rows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < records.size(); rowIndex++) {
            List<String> fields = records.get(rowIndex);
            if (fields.size() != HEADER.size()) {
                throw new IllegalArgumentException("Settlement CSV row " + (rowIndex + 1)
                        + " must contain exactly four columns");
            }

            String providerCode = fields.get(0).trim();
            String providerRef = fields.get(1).trim();
            if (providerCode.isEmpty() || providerRef.isEmpty()) {
                throw new IllegalArgumentException("Settlement CSV provider code and reference are required");
            }

            long amountMinor;
            LocalDate settlementDate;
            try {
                amountMinor = Long.parseLong(fields.get(2).trim());
                settlementDate = LocalDate.parse(fields.get(3).trim());
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Settlement CSV row " + (rowIndex + 1)
                        + " has an invalid amount or date", exception);
            }
            if (amountMinor <= 0) {
                throw new IllegalArgumentException("Settlement CSV amounts must be positive");
            }

            rows.add(new SettlementCsvRow(providerCode, providerRef, amountMinor, settlementDate));
        }
        return List.copyOf(rows);
    }

    private List<List<String>> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            throw new IllegalArgumentException("Settlement CSV is empty");
        }

        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean fieldClosed = false;

        for (int i = 0; i < csv.length(); i++) {
            char current = csv.charAt(i);
            if (quoted) {
                if (current == '"') {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                        fieldClosed = true;
                    }
                } else {
                    field.append(current);
                }
                continue;
            }

            if (fieldClosed && current != ',' && current != '\r' && current != '\n') {
                throw new IllegalArgumentException("Unexpected character after quoted CSV field");
            }
            if (current == '"' && field.isEmpty() && !fieldClosed) {
                quoted = true;
            } else if (current == ',') {
                record.add(field.toString());
                field.setLength(0);
                fieldClosed = false;
            } else if (current == '\n' || current == '\r') {
                if (current == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
                    i++;
                }
                record.add(field.toString());
                records.add(record);
                record = new ArrayList<>();
                field.setLength(0);
                fieldClosed = false;
            } else {
                field.append(current);
            }
        }

        if (quoted) {
            throw new IllegalArgumentException("Settlement CSV contains an unterminated quoted field");
        }
        if (!record.isEmpty() || !field.isEmpty() || fieldClosed) {
            record.add(field.toString());
            records.add(record);
        }
        return records;
    }
}
