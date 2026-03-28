package com.settlement.reconciliation.controller;

import com.settlement.reconciliation.service.SettlementCalculationService;
import com.settlement.reconciliation.service.ReconciliationService;
import com.settlement.reconciliation.model.ReconciliationSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/settlement")
public class SettlementController {

    private final SettlementCalculationService settlementCalculationService;
    private final ReconciliationService reconciliationService;

    public SettlementController(
            SettlementCalculationService settlementCalculationService,
            ReconciliationService reconciliationService) {
        this.settlementCalculationService = settlementCalculationService;
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/calculate/{sessionId}")
    public ResponseEntity<Map<String, Object>> calculateSettlement(@PathVariable String sessionId) {
        try {
            Map<String, Object> result = settlementCalculationService.calculateSettlement(sessionId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to calculate settlement");
            error.put("message", e.getMessage());
            error.put("sessionId", sessionId);
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/reconcile/{sessionId}")
    public ResponseEntity<Map<String, Object>> performReconciliation(@PathVariable String sessionId) {
        try {
            ReconciliationSession session = reconciliationService.performReconciliation(sessionId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("sessionId", sessionId);
            result.put("status", "completed");
            result.put("totalTransactions", session.getTotalTransactions());
            result.put("settledCount", session.getSettledCount());
            result.put("discrepantCount", session.getDiscrepantCount());
            result.put("missingCount", session.getMissingCount());
            result.put("matchRate", session.getMatchRate());
            result.put("netSettlementAmount", session.getNetSettlementAmount());
            result.put("processedAt", session.getProcessedAt());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to perform reconciliation");
            error.put("message", e.getMessage());
            error.put("sessionId", sessionId);
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/summary/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSettlementSummary(@PathVariable String sessionId) {
        try {
            Map<String, Object> summary = reconciliationService.getSessionSummaryReport(sessionId);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to get settlement summary");
            error.put("message", e.getMessage());
            error.put("sessionId", sessionId);
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/analysis/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSettlementAnalysis(@PathVariable String sessionId) {
        try {
            // Combine calculation and reconciliation results for comprehensive analysis
            Map<String, Object> calculation = settlementCalculationService.calculateSettlement(sessionId);
            Map<String, Object> summary = reconciliationService.getSessionSummaryReport(sessionId);
            
            Map<String, Object> analysis = new HashMap<>();
            analysis.put("sessionId", sessionId);
            analysis.put("calculation", calculation);
            analysis.put("summary", summary);
            analysis.put("analysisType", "comprehensive");
            analysis.put("generatedAt", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to generate settlement analysis");
            error.put("message", e.getMessage());
            error.put("sessionId", sessionId);
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/validate/{sessionId}")
    public ResponseEntity<Map<String, Object>> validateSettlement(@PathVariable String sessionId) {
        try {
            Map<String, Object> validation = new HashMap<>();
            
            // Get settlement calculation
            Map<String, Object> calculation = settlementCalculationService.calculateSettlement(sessionId);
            
            // Extract key metrics for validation
            Object netSettlement = calculation.get("netSettlementAmount");
            Object totalTransactions = calculation.get("totalTransactions");
            Object settledTransactions = calculation.get("settledTransactions");
            Object discrepantTransactions = calculation.get("discrepantTransactions");
            
            // Perform validation checks
            boolean isValid = true;
            java.util.List<String> validationErrors = new java.util.ArrayList<>();
            
            if (netSettlement == null) {
                isValid = false;
                validationErrors.add("Net settlement amount is null");
            }
            
            if (totalTransactions == null || (Integer) totalTransactions == 0) {
                isValid = false;
                validationErrors.add("No transactions found for settlement");
            }
            
            // Check if settlement balances
            if (calculation.get("summary") instanceof SettlementCalculationService.SettlementSummary) {
                SettlementCalculationService.SettlementSummary summary = 
                    (SettlementCalculationService.SettlementSummary) calculation.get("summary");
                
                // Validate that payable credits and receivable debits are balanced
                java.math.BigDecimal payableCredit = summary.getPayableTotalCredit();
                java.math.BigDecimal receivableDebit = summary.getReceivableTotalDebit();
                
                if (payableCredit.compareTo(receivableDebit) != 0) {
                    validationErrors.add("Payable credits and receivable debits are not balanced");
                }
            }
            
            validation.put("sessionId", sessionId);
            validation.put("isValid", isValid);
            validation.put("validationErrors", validationErrors);
            validation.put("calculation", calculation);
            validation.put("validatedAt", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(validation);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to validate settlement");
            error.put("message", e.getMessage());
            error.put("sessionId", sessionId);
            return ResponseEntity.badRequest().body(error);
        }
    }
}
