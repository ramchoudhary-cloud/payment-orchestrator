package com.ram.payment_orchestrator.api.reconciliation;

import com.ram.payment_orchestrator.domain.reconciliation.domain.ReconciliationReport;
import com.ram.payment_orchestrator.domain.reconciliation.service.ReconciliationJob;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationJob reconciliationJob;

    @PostMapping(value = "/run", consumes = "text/csv", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReconciliationReport run(@RequestBody String csv) {
        return reconciliationJob.reconcile(csv);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCsv(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }
}
