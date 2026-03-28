package com.settlement.reconciliation.controller;

import com.settlement.reconciliation.model.ReconciliationSession;
import com.settlement.reconciliation.model.ReconciliationResult;
import com.settlement.reconciliation.model.Transaction;
import com.settlement.reconciliation.service.ReconciliationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j

@RestController
@RequestMapping("/api/p2p/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ReconciliationSession>> getAllSessions() {
        List<ReconciliationSession> sessions = reconciliationService.getAllSessions();
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/results/{sessionId}")
    public ResponseEntity<List<ReconciliationResult>> getSessionResults(@PathVariable String sessionId) {
        List<ReconciliationResult> results = reconciliationService.getReconciliationResults(sessionId);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/results/{sessionId}/aggregated")
    public ResponseEntity<List<com.settlement.reconciliation.dto.AggregatedReconciliationResult>> getAggregatedSessionResults(@PathVariable String sessionId) {
        List<com.settlement.reconciliation.dto.AggregatedReconciliationResult> results = reconciliationService.getAggregatedReconciliationResults(sessionId);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/transactions/{sessionId}")
    public ResponseEntity<List<Transaction>> getSessionTransactions(@PathVariable String sessionId) {
        try {
            List<Transaction> transactions = reconciliationService.getTransactionsBySession(sessionId);
            return ResponseEntity.ok(transactions);
        } catch (Exception e) {
            // Return empty list if session not found or error occurs
            return ResponseEntity.ok(List.of());
        }
    }

    @PostMapping("/start")
    public ResponseEntity<Object> startReconciliation(@RequestBody Map<String, Object> payload) {
        try {
            String settlementDate = (String) payload.get("settlementDate");
            @SuppressWarnings("unchecked")
            List<String> fileSessionIds = (List<String>) payload.get("fileSessionIds");
            
            if (settlementDate == null || settlementDate.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "settlementDate is required"));
            }
            
            ReconciliationSession session = reconciliationService.startNewReconciliationSession(settlementDate, fileSessionIds);
            return ResponseEntity.ok(session);
        } catch (IllegalArgumentException e) {
            log.error("Bad request while starting reconciliation session: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error while starting reconciliation session", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to start reconciliation session"));
        }
    }

    @PostMapping("/{sessionId}/perform")
    public ResponseEntity<Object> performReconciliation(@PathVariable String sessionId) {
        try {
            log.info("=== RECONCILIATION PERFORM START: sessionId={} ===", sessionId);
            ReconciliationSession session = reconciliationService.performReconciliation(sessionId);
            log.info("=== RECONCILIATION PERFORM COMPLETE: sessionId={}, totalTx={}, settled={}, matchRate={} ===", 
                    sessionId, session.getTotalTransactions(), session.getSettledCount(), session.getMatchRate());
            return ResponseEntity.ok(session);
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException during reconciliation for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Exception during reconciliation for session {}:", sessionId, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to perform reconciliation"));
        }
    }

@GetMapping("/{sessionId}/summary")
    public ResponseEntity<Map<String, Object>> getSessionSummary(@PathVariable String sessionId) {
        Map<String, Object> summary = reconciliationService.getSessionSummaryReport(sessionId);
        return ResponseEntity.ok(summary);
    }
}
