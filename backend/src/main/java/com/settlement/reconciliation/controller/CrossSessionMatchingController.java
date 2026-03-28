package com.settlement.reconciliation.controller;

import com.settlement.reconciliation.dto.CrossSessionMatchDto;
import com.settlement.reconciliation.dto.UploadResultDto;
import com.settlement.reconciliation.model.CrossSessionMatch;
import com.settlement.reconciliation.model.Transaction;
import com.settlement.reconciliation.model.UnresolvedTransaction;
import com.settlement.reconciliation.repository.UnresolvedTransactionRepository;
import com.settlement.reconciliation.service.CrossSessionMatchingService;
import com.settlement.reconciliation.service.FileUploadService;
import com.settlement.reconciliation.util.StanUtils;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.stream.Collectors;
import java.io.IOException;

@RestController
@RequestMapping("/api/p2p/cross-session-matches")
@Slf4j
public class CrossSessionMatchingController {
    
    private final FileUploadService fileUploadService;
    private final CrossSessionMatchingService crossSessionMatchingService;
    private final UnresolvedTransactionRepository unresolvedTransactionRepository;
    
    public CrossSessionMatchingController(
            FileUploadService fileUploadService,
            CrossSessionMatchingService crossSessionMatchingService,
            UnresolvedTransactionRepository unresolvedTransactionRepository) {
        this.fileUploadService = fileUploadService;
        this.crossSessionMatchingService = crossSessionMatchingService;
        this.unresolvedTransactionRepository = unresolvedTransactionRepository;
    }
    
    /**
     * Get all cross-session matches
     */
    @GetMapping
    public ResponseEntity<List<CrossSessionMatchDto>> getAllMatches() {
        // This would need to be implemented in the service
        // For now, return empty list
        return ResponseEntity.ok(List.of());
    }
    
    /**
     * Get cross-session matches for a specific session
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<List<CrossSessionMatchDto>> getMatchesBySession(@PathVariable String sessionId) {
        try {
            List<CrossSessionMatch> matches = crossSessionMatchingService.getMatchesBySession(sessionId);
            List<CrossSessionMatchDto> dtos = matches.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            log.error("Error getting matches for session {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get all unresolved transactions
     */
    @GetMapping("/unresolved-transactions")
    public ResponseEntity<List<UploadResultDto.UnresolvedTransactionDto>> getUnresolvedTransactions() {
        // This would need to be implemented in the service
        // For now, return empty list
        return ResponseEntity.ok(List.of());
    }
    
    /**
     * Create manual match between transactions (used for multi-file uploads)
     */
    @PostMapping("/manual")
    public ResponseEntity<Map<String, Object>> createManualMatch(@RequestBody ManualMatchRequest request) {
        try {
            log.info("Creating manual match for {} transactions", request.getOriginalTransactions().size());
            
            // Convert frontend unresolved transactions to backend entities
            List<UnresolvedTransaction> unresolvedTxList = new ArrayList<>();
            for (Map<String, Object> txData : request.getOriginalTransactions()) {
                UnresolvedTransaction tx = new UnresolvedTransaction();
                tx.setId((String) txData.get("id"));
                tx.setStan(StanUtils.extractStan((String) txData.get("stan")));
                tx.setTransactionRef((String) txData.get("transactionRef"));
                tx.setAmount(txData.get("amount") != null ? BigDecimal.valueOf(((Number) txData.get("amount")).doubleValue()) : null);
                tx.setTransactionDate(parseDateString((String) txData.get("transactionDate")));
                tx.setTerminalId((String) txData.get("terminalId"));
                tx.setStatus(UnresolvedTransaction.TransactionStatus.DISCREPANT);
                tx.setOriginalSessionId((String) txData.get("originalSessionId"));
                tx.setCreatedAt(parseDateString((String) txData.get("createdAt")));
                unresolvedTxList.add(tx);
            }
            
            // Perform cross-session matching
            List<CrossSessionMatch> matches = crossSessionMatchingService.processNewTransactions(unresolvedTxList);
            
            // Return match results
            Map<String, Object> result = new HashMap<>();
            result.put("crossSessionMatches", matches.stream().map(this::convertToDto).collect(Collectors.toList()));
            result.put("integratedFromPreviousSessions", matches.stream().mapToInt(m -> 1).sum());
            result.put("totalMatches", matches.size());
            
            log.info("Manual matching completed: {} matches found", matches.size());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error creating manual match", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to create manual match: " + e.getMessage()));
        }
    }
    
    /**
     * Reject a match
     */
    @PutMapping("/{matchId}/reject")
    public ResponseEntity<Void> rejectMatch(@PathVariable String matchId, 
                                           @RequestBody RejectMatchRequest request) {
        try {
            crossSessionMatchingService.rejectMatch(matchId, request.getReason(), request.getReviewedBy());
            return ResponseEntity.ok().build();
            
        } catch (IllegalArgumentException e) {
            log.error("Error rejecting match: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error rejecting match", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get cross-session matching statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getMatchingStatistics() {
        Map<String, Object> stats = crossSessionMatchingService.getMatchingStatistics();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Upload file with cross-session matching enabled
     */
    @PostMapping("/upload/{fileType}/with-cross-session-match")
    public ResponseEntity<UploadResultDto> uploadWithCrossSessionMatch(
            @PathVariable String fileType,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "enableCrossSessionMatch", defaultValue = "true") boolean enableCrossSessionMatch) {

        try {
            log.info("=== CROSS-SESSION UPLOAD START: type={} (matching={}), file={}", fileType, enableCrossSessionMatch, file.getOriginalFilename());
            Map<String, Object> parsedReport = fileUploadService.handleFileUpload(file);

            @SuppressWarnings("unchecked")
            List<Transaction> parsedTransactions = (List<Transaction>) parsedReport.getOrDefault("parsedData", Collections.emptyList());
            String sessionId = (String) parsedReport.getOrDefault("sessionId", null);
            if (sessionId == null) {
                sessionId = java.util.UUID.randomUUID().toString();
            }
            
            final String finalSessionId = sessionId;

            UploadResultDto result = new UploadResultDto();
            result.setSessionId(sessionId);
            int totalProcessed = parsedTransactions.size();
            result.setTotalProcessed(totalProcessed);
            result.setSettled(0);
            result.setDiscrepant(totalProcessed);
            result.setMissing(0);
            result.setDuplicate(0);
            result.setCrossSessionMatches(new ArrayList<>());
            result.setUnresolvedTransactions(new ArrayList<>());

            List<UnresolvedTransaction> unresolvedTransactions = parsedTransactions.stream()
                    .map(tx -> mapToUnresolvedTransaction(tx, finalSessionId, fileType))
                    .collect(Collectors.toList());

            List<UnresolvedTransaction> savedUnresolved = unresolvedTransactions.isEmpty()
                    ? Collections.emptyList()
                    : unresolvedTransactionRepository.saveAll(unresolvedTransactions);

            List<UploadResultDto.UnresolvedTransactionDto> unresolvedDtos = savedUnresolved.stream()
                    .map(this::convertUnresolvedToDto)
                    .collect(Collectors.toCollection(ArrayList::new));
            result.setUnresolvedTransactions(unresolvedDtos);

            if (enableCrossSessionMatch && !savedUnresolved.isEmpty()) {
                log.info("=== STARTING CROSS-SESSION MATCHING: {} unresolved txns ===", savedUnresolved.size());
                List<CrossSessionMatch> matches = crossSessionMatchingService.processNewTransactions(savedUnresolved);
                log.info("=== CROSS-SESSION MATCHING COMPLETE: {} matches found ===", matches.size());
                List<CrossSessionMatchDto> matchDtos = matches.stream()
                        .map(this::convertToDto)
                        .collect(Collectors.toCollection(ArrayList::new));
                result.setCrossSessionMatches(matchDtos);
                result.setIntegratedFromPreviousSessions(matches.size());
            }

            log.info("Cross-session upload parsed {} transactions and generated {} match(es)", totalProcessed, result.getCrossSessionMatches().size());
            return ResponseEntity.ok(result);

        } catch (IOException e) {
            log.error("Failed to parse uploaded file for cross-session matching", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error uploading file with cross-session matching", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    // Helper methods
    private CrossSessionMatchDto convertToDto(CrossSessionMatch match) {
        CrossSessionMatchDto dto = new CrossSessionMatchDto();
        dto.setId(match.getId());
        dto.setOriginalTransactionId(match.getOriginalTransactionId());
        dto.setOriginalSessionId(match.getOriginalSessionId());
        dto.setMatchedTransactionId(match.getMatchedTransactionId());
        dto.setMatchedSessionId(match.getMatchedSessionId());
        dto.setMatchType(match.getMatchType());
        dto.setConfidence(match.getConfidence());
        dto.setMatchedAt(match.getMatchedAt());
        dto.setResolvedBy(match.getResolvedBy());
        dto.setMatchNotes(match.getMatchNotes());
        dto.setReviewStatus(match.getReviewStatus());
        dto.setReviewedBy(match.getReviewedBy());
        dto.setReviewedAt(match.getReviewedAt());
        dto.setRejectionReason(match.getRejectionReason());
        return dto;
    }

    private UnresolvedTransaction mapToUnresolvedTransaction(Transaction transaction, String sessionId, String fileType) {
        UnresolvedTransaction unresolved = new UnresolvedTransaction();
        unresolved.setStan(StanUtils.extractStan(transaction.getStanNo()));
        unresolved.setTransactionRef(transaction.getTransRef());
        unresolved.setAmount(determineAmount(transaction));
        LocalDateTime txnDate = transaction.getValueDate() != null ? transaction.getValueDate() : transaction.getBookingDate();
        unresolved.setTransactionDate(txnDate);
        unresolved.setTerminalId(transaction.getTerminalId());
        unresolved.setStatus(UnresolvedTransaction.TransactionStatus.DISCREPANT);
        String details = transaction.getDescription();
        if (details == null || details.isBlank()) {
            details = transaction.getTransactionDescription();
        }
        unresolved.setDetails(details);
        unresolved.setOriginalSessionId(sessionId);
        unresolved.setSourceFiles(fileType != null ? fileType.toUpperCase() : transaction.getSource());
        return unresolved;
    }

    private BigDecimal determineAmount(Transaction transaction) {
        if (transaction.getTxnAmount() != null) {
            return transaction.getTxnAmount();
        }
        if (transaction.getCredit() != null && transaction.getCredit().compareTo(BigDecimal.ZERO) != 0) {
            return transaction.getCredit();
        }
        if (transaction.getDebit() != null && transaction.getDebit().compareTo(BigDecimal.ZERO) != 0) {
            return transaction.getDebit();
        }
        return BigDecimal.ZERO;
    }

    private UploadResultDto.UnresolvedTransactionDto convertUnresolvedToDto(UnresolvedTransaction transaction) {
        UploadResultDto.UnresolvedTransactionDto dto = new UploadResultDto.UnresolvedTransactionDto();
        dto.setId(transaction.getId());
        dto.setStan(transaction.getStan());
        dto.setTransactionRef(transaction.getTransactionRef());
        dto.setAmount(transaction.getAmount() != null ? transaction.getAmount().doubleValue() : null);
        dto.setTransactionDate(transaction.getTransactionDate() != null
                ? transaction.getTransactionDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null);
        dto.setTerminalId(transaction.getTerminalId());
        dto.setStatus(transaction.getStatus());
        dto.setDiscrepancyType(transaction.getDiscrepancyType());
        dto.setDetails(transaction.getDetails());
        dto.setOriginalSessionId(transaction.getOriginalSessionId());
        dto.setCreatedAt(transaction.getCreatedAt() != null
                ? transaction.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null);
        return dto;
    }
    
    // Helper method to parse date strings flexibly
    private LocalDateTime parseDateString(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Try ISO format with Z (UTC)
            if (dateStr.endsWith("Z")) {
                return ZonedDateTime.parse(dateStr).toLocalDateTime();
            }
            // Try standard LocalDateTime format
            return LocalDateTime.parse(dateStr);
        } catch (Exception e) {
            // Try common formats
            try {
                return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e2) {
                log.warn("Could not parse date string: {}", dateStr);
                return null;
            }
        }
    }
    
    // Request DTOs
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManualMatchRequest {
        private List<Map<String, Object>> originalTransactions;
        private String reviewedBy;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RejectMatchRequest {
        private String reason;
        private String reviewedBy;
    }
}
