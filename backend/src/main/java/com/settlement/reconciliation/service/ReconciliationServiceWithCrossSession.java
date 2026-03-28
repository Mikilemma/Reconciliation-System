package com.settlement.reconciliation.service;

import com.settlement.reconciliation.model.UnresolvedTransaction;
import com.settlement.reconciliation.dto.UploadResultDto;
import com.settlement.reconciliation.dto.CrossSessionMatchDto;
import com.settlement.reconciliation.repository.UnresolvedTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced reconciliation service that integrates cross-session matching
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationServiceWithCrossSession {
    
    private final CrossSessionMatchingService crossSessionMatchingService;
    private final ReportIntegrationService reportIntegrationService;
    private final UnresolvedTransactionRepository unresolvedTransactionRepository;
    
    /**
     * Process reconciliation results with cross-session matching
     */
    @Transactional
    public UploadResultDto processReconciliationWithCrossSession(
            String sessionId, 
            List<TransactionResult> transactionResults,
            boolean enableCrossSessionMatching) {
        
        log.info("Processing reconciliation for session {} with cross-session matching: {}", 
                sessionId, enableCrossSessionMatching);
        
        // Process transaction results
        UploadResultDto result = new UploadResultDto();
        result.setSessionId(sessionId);
        result.setTotalProcessed(transactionResults.size());
        
        int settled = 0, discrepant = 0, missing = 0, duplicate = 0;
        List<UnresolvedTransaction> unresolvedTransactions = new ArrayList<>();
        
        // Categorize transactions
        for (TransactionResult txResult : transactionResults) {
            switch (txResult.getStatus()) {
                case SETTLED:
                    settled++;
                    break;
                case DISCREPANT:
                    discrepant++;
                    unresolvedTransactions.add(createUnresolvedTransaction(txResult, sessionId));
                    break;
                case MISSING:
                    missing++;
                    unresolvedTransactions.add(createUnresolvedTransaction(txResult, sessionId));
                    break;
                case DUPLICATE:
                    duplicate++;
                    unresolvedTransactions.add(createUnresolvedTransaction(txResult, sessionId));
                    break;
            }
        }
        
        result.setSettled(settled);
        result.setDiscrepant(discrepant);
        result.setMissing(missing);
        result.setDuplicate(duplicate);
        
        // Save unresolved transactions
        List<UnresolvedTransaction> savedUnresolved = unresolvedTransactionRepository.saveAll(unresolvedTransactions);
        
        // Convert to DTO
        List<UploadResultDto.UnresolvedTransactionDto> unresolvedDtos = savedUnresolved.stream()
                .map(this::convertToDto)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        result.setUnresolvedTransactions(unresolvedDtos);
        
        // Perform cross-session matching if enabled
        List<CrossSessionMatchDto> crossSessionMatches = new ArrayList<>();
        if (enableCrossSessionMatching && !savedUnresolved.isEmpty()) {
            try {
                List<com.settlement.reconciliation.model.CrossSessionMatch> matches = 
                        crossSessionMatchingService.processNewTransactions(savedUnresolved);
                
                crossSessionMatches = matches.stream()
                        .map(this::convertToDto)
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
                
                log.info("Cross-session matching found {} matches", matches.size());
                
            } catch (Exception e) {
                log.error("Error during cross-session matching", e);
                // Continue without cross-session matches
            }
        }
        
        result.setCrossSessionMatches(crossSessionMatches);
        
        // Integrate cross-session matches into the current report
        if (enableCrossSessionMatching && !crossSessionMatches.isEmpty()) {
            try {
                result = reportIntegrationService.integrateMatchesIntoReport(result, sessionId);
                log.info("Successfully integrated cross-session matches into report for session: {}", sessionId);
            } catch (Exception e) {
                log.error("Error integrating cross-session matches into report", e);
                // Continue without integration - the basic matching still works
            }
        }
        
        log.info("Reconciliation completed for session {}: {} total, {} settled, {} unresolved, {} matches found, {} integrated",
                sessionId, result.getTotalProcessed(), result.getSettled(), 
                result.getUnresolvedTransactions().size(), result.getCrossSessionMatches().size(),
                result.getIntegratedFromPreviousSessions());
        
        return result;
    }
    
    /**
     * Create UnresolvedTransaction from TransactionResult
     */
    private UnresolvedTransaction createUnresolvedTransaction(TransactionResult txResult, String sessionId) {
        UnresolvedTransaction unresolved = new UnresolvedTransaction();
        unresolved.setId(txResult.getId());
        unresolved.setStan(txResult.getStan());
        unresolved.setTransactionRef(txResult.getTransactionRef());
        unresolved.setAmount(txResult.getAmount());
        unresolved.setTransactionDate(parseDateTime(txResult.getTransactionDate()));
        unresolved.setTerminalId(txResult.getTerminalId());
        unresolved.setStatus(UnresolvedTransaction.TransactionStatus.valueOf(txResult.getStatus().name()));
        unresolved.setDiscrepancyType(txResult.getDiscrepancyType());
        unresolved.setDetails(txResult.getDetails());
        unresolved.setOriginalSessionId(sessionId);
        unresolved.setSourceFiles(txResult.getSourceFiles());
        
        return unresolved;
    }
    
    /**
     * Parse date string to LocalDateTime
     */
    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Try different date formats
            DateTimeFormatter[] formatters = {
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
            };
            
            for (DateTimeFormatter formatter : formatters) {
                try {
                    return LocalDateTime.parse(dateStr, formatter);
                } catch (Exception e) {
                    // Try next formatter
                }
            }
            
            log.warn("Could not parse date: {}", dateStr);
            return null;
            
        } catch (Exception e) {
            log.error("Error parsing date: {}", dateStr, e);
            return null;
        }
    }
    
    /**
     * Convert UnresolvedTransaction to DTO
     */
    private UploadResultDto.UnresolvedTransactionDto convertToDto(UnresolvedTransaction tx) {
        UploadResultDto.UnresolvedTransactionDto dto = new UploadResultDto.UnresolvedTransactionDto();
        dto.setId(tx.getId());
        dto.setStan(tx.getStan());
        dto.setTransactionRef(tx.getTransactionRef());
        dto.setAmount(tx.getAmount() != null ? tx.getAmount().doubleValue() : null);
        dto.setTransactionDate(tx.getTransactionDate() != null ? 
                tx.getTransactionDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
        dto.setTerminalId(tx.getTerminalId());
        dto.setStatus(tx.getStatus());
        dto.setDiscrepancyType(tx.getDiscrepancyType());
        dto.setDetails(tx.getDetails());
        dto.setOriginalSessionId(tx.getOriginalSessionId());
        dto.setCreatedAt(tx.getCreatedAt() != null ? 
                tx.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
        return dto;
    }
    
    /**
     * Convert CrossSessionMatch to DTO
     */
    private CrossSessionMatchDto convertToDto(com.settlement.reconciliation.model.CrossSessionMatch match) {
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
    
    /**
     * Transaction result representation
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TransactionResult {
        private String id;
        private String stan;
        private String transactionRef;
        private BigDecimal amount;
        private String transactionDate;
        private String terminalId;
        private TransactionStatus status;
        private String discrepancyType;
        private String details;
        private String sourceFiles;
        
        public enum TransactionStatus {
            SETTLED, DISCREPANT, MISSING, DUPLICATE, PENDING
        }
    }
}
