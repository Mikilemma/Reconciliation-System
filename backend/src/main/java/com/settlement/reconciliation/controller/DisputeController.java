package com.settlement.reconciliation.controller;

import com.settlement.reconciliation.model.Dispute;
import com.settlement.reconciliation.model.AuditLog;
import com.settlement.reconciliation.dto.AggregatedDispute;
import com.settlement.reconciliation.dto.AggregatedDisputePageResponse;
import com.settlement.reconciliation.service.DisputeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/p2p/disputes")
public class DisputeController {

    private final DisputeService disputeService;

    public DisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    /**
     * Get all disputes (raw, non-aggregated).
     * Kept for backward compatibility.
     */
    @GetMapping
    public ResponseEntity<List<Dispute>> getAllDisputes(@RequestParam(required = false) String status) {
        if (status != null && !status.isEmpty()) {
            return ResponseEntity.ok(disputeService.getDisputesByStatus(status));
        }
        return ResponseEntity.ok(disputeService.getAllDisputes());
    }

    /**
     * Get aggregated disputes - consolidates duplicate records for the same transaction
     * into single entries for cleaner UI display.
     */
    @GetMapping("/aggregated")
    public ResponseEntity<List<AggregatedDispute>> getAggregatedDisputes(@RequestParam(required = false) String status) {
        if (status != null && !status.isEmpty()) {
            return ResponseEntity.ok(disputeService.getAggregatedDisputesByStatus(status));
        }
        return ResponseEntity.ok(disputeService.getAggregatedDisputes());
    }

    @GetMapping("/aggregated/page")
    public ResponseEntity<AggregatedDisputePageResponse> getAggregatedDisputesPage(
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String discrepancyType,
        @RequestParam(required = false) String transactionType,
        @RequestParam(required = false) String bank,
        @RequestParam(required = false) String onUs,
        @RequestParam(required = false) String sessionId,
        @RequestParam(required = false) String sessionDate,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(
            disputeService.getAggregatedDisputesPage(
                search, status, discrepancyType, transactionType, bank, onUs, sessionId, sessionDate, page, size
            )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Dispute> getDisputeById(@PathVariable String id) {
        Optional<Dispute> dispute = disputeService.getDisputeById(id);
        return dispute.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Dispute> updateDisputeStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> payload,
            Authentication authentication) {
        String newStatus = payload.get("newStatus");
        String resolutionNotes = payload.get("resolutionNotes");
        String resolvedBy = authentication != null ? authentication.getName() : "unknown";

        if (newStatus == null || newStatus.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Dispute updatedDispute = disputeService.updateDisputeStatus(id, newStatus, resolutionNotes, resolvedBy);
            return ResponseEntity.ok(updatedDispute);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Update status for all underlying disputes in an aggregated dispute.
     * This resolves all records for the same transaction at once.
     */
    @PutMapping("/aggregated/status")
    public ResponseEntity<Void> updateAggregatedDisputeStatus(@RequestBody Map<String, Object> payload, Authentication authentication) {
        @SuppressWarnings("unchecked")
        List<String> disputeIds = (List<String>) payload.get("disputeIds");
        String newStatus = (String) payload.get("newStatus");
        String resolutionNotes = (String) payload.get("resolutionNotes");
        String resolvedBy = authentication != null ? authentication.getName() : "unknown";

        if (disputeIds == null || disputeIds.isEmpty() || newStatus == null || newStatus.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            disputeService.updateAggregatedDisputeStatus(disputeIds, newStatus, resolutionNotes, resolvedBy);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<List<AuditLog>> getAuditLogsByDispute(@PathVariable String id) {
        return ResponseEntity.ok(disputeService.getAuditLogsByDisputeId(id));
    }

    @PostMapping("/audit")
    public ResponseEntity<List<AuditLog>> getAuditLogsByDisputes(@RequestBody Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        List<String> disputeIds = (List<String>) payload.get("disputeIds");
        return ResponseEntity.ok(disputeService.getAuditLogsByDisputeIds(disputeIds));
    }
}
